package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 债券多代理回归器，用一组债券 ETF 的最小二乘组合解释基金日收益，替代单代理比值法
 */
@Slf4j
@Component
public class BondMultiProxyEstimator {

    private static final int SCALE = 8;
    private static final int OUTPUT_SCALE = 6;
    private static final double MIN_DAILY_MOVE = 0.01;

    /**
     * 用一组代理的日收益做带截距的最小二乘，样本不足或代理无波动时返回 null
     *
     * @param netWorths 基金历史净值，按日期升序
     * @param proxyBars 代理代码到日线的映射，按日期升序
     * @param window    回看窗口，交易日
     * @param minPairs  最少配对样本
     * @return 回归结果，样本不足返回 null
     */
    public Model estimate(List<FundNetWorthPoint> netWorths, Map<String, List<MarketBar>> proxyBars,
                          int window, int minPairs) {
        List<String> codes = new ArrayList<>(proxyBars.keySet());
        if (codes.isEmpty()) {
            return null;
        }
        List<Object[]> aligned = alignReturns(netWorths, codes, proxyBars);
        int need = minPairs <= 0 ? 1 : minPairs;
        if (aligned.size() < need) {
            log.info("债券多代理样本不足 {} < {}，退回直取代理", aligned.size(), need);
            return null;
        }
        List<Object[]> used = window > 0 && aligned.size() > window
                ? aligned.subList(aligned.size() - window, aligned.size()) : aligned;
        int size = used.size();
        int dim = codes.size();
        // 设计矩阵带一列截距，最后解出 [α, β1..βk]
        double[][] design = new double[size][dim + 1];
        double[] target = new double[size];
        for (int i = 0; i < size; i++) {
            design[i][0] = 1.0;
            double[] row = (double[]) used.get(i)[0];
            for (int j = 0; j < dim; j++) {
                design[i][j + 1] = row[j];
            }
            target[i] = (double) used.get(i)[1];
        }
        double[] coef = solveNonNegative(design, target, dim);
        if (coef == null) {
            log.info("债券多代理回归解不出系数，退回直取代理");
            return null;
        }
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        for (int j = 0; j < dim; j++) {
            weights.put(codes.get(j), scale(coef[j + 1]));
        }
        Model model = new Model(scale(coef[0]), weights, size, meanAbs(target));
        log.info("债券多代理回归完成 样本={} α={} 系数={} 有效代理={}", size, model.intercept(), weights,
                model.proxyCount());
        return model;
    }

    /**
     * 用回归系数把当日各代理涨跌合成该基金的估计涨跌，超出自身量级时截断
     *
     * @param model       回归模型
     * @param proxyChange 代理代码到当日涨跌幅的映射，百分点
     * @return 预估涨跌幅，百分点
     */
    public BigDecimal predict(Model model, Map<String, BigDecimal> proxyChange) {
        if (model == null || proxyChange == null) {
            return null;
        }
        double value = model.intercept().doubleValue();
        boolean anyHit = false;
        for (Map.Entry<String, BigDecimal> entry : model.weights().entrySet()) {
            BigDecimal change = proxyChange.get(entry.getKey());
            if (change == null) {
                continue;
            }
            value += entry.getValue().doubleValue() * change.doubleValue();
            anyHit = true;
        }
        if (!anyHit) {
            return null;
        }
        BigDecimal result = BigDecimal.valueOf(value).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
        // 债券单日涨跌极少超过自身波动的一个量级，超出说明代理当日有异动，不能照搬
        BigDecimal cap = model.fundMeanAbsChange().multiply(BigDecimal.valueOf(4));
        if (cap.signum() > 0 && result.abs().compareTo(cap) > 0) {
            return result.signum() > 0 ? cap.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP)
                    : cap.negate().setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
        }
        return result;
    }

    /**
     * 按交易日对齐基金收益与各代理收益，只保留全部代理都有数据的日子
     *
     * @param netWorths 基金净值
     * @param codes     代理代码
     * @param proxyBars 代理日线
     * @return 每项为 [代理收益数组, 基金收益]
     */
    private List<Object[]> alignReturns(List<FundNetWorthPoint> netWorths, List<String> codes,
                                        Map<String, List<MarketBar>> proxyBars) {
        List<Object[]> result = new ArrayList<>();
        if (netWorths == null || netWorths.isEmpty()) {
            return result;
        }
        List<Map<LocalDate, Double>> proxyReturns = new ArrayList<>(codes.size());
        for (String code : codes) {
            proxyReturns.add(dailyReturns(proxyBars.get(code)));
        }
        for (FundNetWorthPoint point : netWorths) {
            if (point.getEquityReturn() == null) {
                continue;
            }
            double[] row = new double[codes.size()];
            boolean complete = true;
            for (int j = 0; j < codes.size(); j++) {
                Double value = proxyReturns.get(j).get(point.getNetWorthDate());
                if (value == null) {
                    complete = false;
                    break;
                }
                row[j] = value;
            }
            if (complete) {
                result.add(new Object[]{row, point.getEquityReturn().doubleValue()});
            }
        }
        return result;
    }

    /**
     * 日线转「日期到日收益」映射，单位百分点
     *
     * @param bars 日线
     * @return 日收益映射，入参为空时返回空表
     */
    private Map<LocalDate, Double> dailyReturns(List<MarketBar> bars) {
        Map<LocalDate, Double> returns = new LinkedHashMap<>();
        if (bars == null || bars.size() < 2) {
            return returns;
        }
        for (int i = 1; i < bars.size(); i++) {
            BigDecimal previous = bars.get(i - 1).getClose();
            BigDecimal current = bars.get(i).getClose();
            if (previous == null || current == null || previous.signum() == 0) {
                continue;
            }
            returns.put(bars.get(i).getDate(), current.subtract(previous)
                    .divide(previous, SCALE, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    .doubleValue());
        }
        return returns;
    }

    /**
     * 带非负约束的最小二乘，负系数逐个剔除后重解，最多迭代代理个数次
     *
     * @param design 设计矩阵，第一列为截距
     * @param target 目标值
     * @param dim    代理个数
     * @return 系数，[0] 是截距，其余按原代理顺序，剔除的位置为 0
     */
    private double[] solveNonNegative(double[][] design, double[] target, int dim) {
        boolean[] dropped = new boolean[dim];
        for (int round = 0; round <= dim; round++) {
            List<Integer> alive = new ArrayList<>();
            for (int j = 0; j < dim; j++) {
                if (!dropped[j]) {
                    alive.add(j);
                }
            }
            if (alive.isEmpty()) {
                // 所有代理都被剔除，退化为只估截距
                double mean = meanOf(target);
                double[] only = new double[dim + 1];
                only[0] = mean;
                return only;
            }
            int[] keep = alive.stream().mapToInt(Integer::intValue).toArray();
            double[] coef = solveLeastSquares(subDesign(design, keep), target);
            if (coef == null) {
                return null;
            }
            // 找出系数为负的代理，剔除后重解，避免出现「代理涨基金跌」的反向权重
            int worst = -1;
            for (int i = 0; i < keep.length; i++) {
                if (coef[i + 1] < 0 && (worst < 0 || coef[i + 1] < coef[worst + 1])) {
                    worst = i;
                }
            }
            if (worst < 0) {
                double[] full = new double[dim + 1];
                full[0] = coef[0];
                for (int i = 0; i < keep.length; i++) {
                    full[keep[i] + 1] = coef[i + 1];
                }
                return full;
            }
            dropped[keep[worst]] = true;
        }
        return null;
    }

    /**
     * 按保留的列取子设计矩阵，第一列截距始终保留
     *
     * @param design 原设计矩阵
     * @param keep   保留的代理下标
     * @return 子矩阵
     */
    private double[][] subDesign(double[][] design, int[] keep) {
        double[][] sub = new double[design.length][keep.length + 1];
        for (int i = 0; i < design.length; i++) {
            sub[i][0] = design[i][0];
            for (int j = 0; j < keep.length; j++) {
                sub[i][j + 1] = design[i][keep[j] + 1];
            }
        }
        return sub;
    }

    /**
     * 求均值
     *
     * @param values 序列
     * @return 均值
     */
    private double meanOf(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /**
     * 解正规方程求最小二乘系数，矩阵奇异时用岭回归兜底
     *
     * @param design 设计矩阵，第一列为截距
     * @param target 目标值
     * @return 系数，无法求解返回 null
     */
    private double[] solveLeastSquares(double[][] design, double[] target) {
        int rows = design.length;
        int cols = design[0].length;
        double[][] xtx = new double[cols][cols];
        double[] xty = new double[cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                xty[j] += design[i][j] * target[i];
                for (int k = 0; k < cols; k++) {
                    xtx[j][k] += design[i][j] * design[i][k];
                }
            }
        }
        double[] coef = gaussJordan(xtx, xty);
        if (coef != null) {
            return coef;
        }
        // 代理之间高度共线时正规矩阵接近奇异，加一点对角线扰动取稳定解
        double trace = 0;
        for (int i = 0; i < cols; i++) {
            trace += xtx[i][i];
        }
        double lambda = trace / cols * 1e-6 + 1e-9;
        for (int i = 0; i < cols; i++) {
            // 截距不惩罚，否则会把整体水平项一起压掉
            if (i > 0) {
                xtx[i][i] += lambda;
            }
        }
        return gaussJordan(xtx, xty);
    }

    /**
     * 高斯-约当消元解线性方程组
     *
     * @param matrix 系数矩阵，会被原地修改
     * @param rhs    右端向量
     * @return 解，主元过小返回 null
     */
    private double[] gaussJordan(double[][] matrix, double[] rhs) {
        int n = rhs.length;
        double[] b = rhs.clone();
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(matrix[row][col]) > Math.abs(matrix[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(matrix[pivot][col]) < 1e-12) {
                return null;
            }
            double[] tmpRow = matrix[col];
            matrix[col] = matrix[pivot];
            matrix[pivot] = tmpRow;
            double tmpVal = b[col];
            b[col] = b[pivot];
            b[pivot] = tmpVal;

            double diag = matrix[col][col];
            for (int k = col; k < n; k++) {
                matrix[col][k] /= diag;
            }
            b[col] /= diag;
            for (int row = 0; row < n; row++) {
                if (row == col) {
                    continue;
                }
                double factor = matrix[row][col];
                if (factor == 0) {
                    continue;
                }
                for (int k = col; k < n; k++) {
                    matrix[row][k] -= factor * matrix[col][k];
                }
                b[row] -= factor * b[col];
            }
        }
        return b;
    }

    /**
     * 求绝对值均值，用于给预测封顶
     *
     * @param values 目标值
     * @return 绝对值均值
     */
    private BigDecimal meanAbs(double[] values) {
        if (values.length == 0) {
            return BigDecimal.ZERO;
        }
        double sum = 0;
        for (double value : values) {
            sum += Math.abs(value);
        }
        return scale(sum / values.length);
    }

    /**
     * 统一保留六位小数
     *
     * @param value 原始值
     * @return 保留后的值
     */
    private BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 债券多代理回归模型，截距与各代理权重一起给出
     */
    public record Model(BigDecimal intercept, Map<String, BigDecimal> weights, int samples,
                        BigDecimal fundMeanAbsChange) {

        /**
         * 参与回归的代理个数，权重为 0 的（共线或反向被剔除）不计
         *
         * @return 有效代理个数
         */
        public int proxyCount() {
            return (int) weights.values().stream().filter(w -> w != null && w.signum() != 0).count();
        }
    }
}
