declare namespace API {
  type DeleteRequest = {
    /** id */
    id?: number;
  };

  type FundCandidateVO = {
    /** 业务主键，如 OF:000001 */
    varietyKey?: string;
    /** 品种类型 */
    varietyType?: "OF" | "ETF" | "IDX";
    /** 品种类型中文名 */
    varietyTypeLabel?: string;
    /** 6 位代码 */
    varietyCode?: string;
    /** 标的名称 */
    varietyName?: string;
    /** 基金类型 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 基金类型中文名 */
    categoryLabel?: string;
    /** 类型是否已确定，false 表示须使用者手动指定 */
    confirmed?: boolean;
    /** 跟踪指数代码，红利基金用于查股息率 */
    trackIndexCode?: string;
    /** 是否可从持仓入口录入，指数与股票为 false */
    recordable?: boolean;
    /** 身份提示，说明判定依据或需要注意的地方 */
    note?: string;
  };

  type FundCatalogStatusVO = {
    /** 缓存总条数 */
    total?: number;
    /** 最近一次同步时间 */
    syncTime?: string;
    /** 是否已过期，过期时下次消歧会触发同步 */
    stale?: boolean;
    /** 允许的最大缓存天数 */
    maxAgeDays?: number;
  };

  type FundCatalogSyncVO = {
    /** 同步后的总条数 */
    total?: number;
    /** 同步完成时间 */
    syncTime?: string;
    /** 同步耗时毫秒 */
    costMs?: number;
  };

  type FundConfirmRequest = {
    /** 6 位代码 */
    code?: string;
    /** 品种类型，只能是场外基金或场内 ETF */
    varietyType?: "OF" | "ETF" | "IDX";
    /** 基金类型，自动判定为待确认时必填 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 跟踪指数代码，红利基金必填以取股息率 */
    trackIndexCode?: string;
  };

  type FundDisambiguateRequest = {
    /** 6 位基金或在市代码 */
    code?: string;
  };

  type FundDisambiguateVO = {
    /** 查询的 6 位代码 */
    code?: string;
    /** 是否命中任何候选 */
    exists?: boolean;
    /** 候选列表，按场外基金、场内 ETF、指数顺序 */
    candidates?: FundCandidateVO[];
    /** 名称相近的其他份额，A/C 类并列展示供核对 */
    relatedShareClasses?: FundCandidateVO[];
    /** 同码股票提示，股票不从持仓入口录入 */
    stockHint?: string;
    /** 是否存在类型待确认的候选 */
    needConfirm?: boolean;
    /** 是否降级，指数校验失败时为 true */
    degraded?: boolean;
    /** 基金全集缓存的数据时间 */
    catalogUpdatedAt?: string;
  };

  type getByIdParams = {
    id?: number;
  };

  type getByIdParams = {
    id?: number;
  };

  type getByIdParams = {
    id?: number;
  };

  type HoldingAddRequest = {
    /** 所持品种的业务主键 */
    varietyKey?: string;
    /** 持有份额 */
    shares?: number;
    /** 成本价 */
    costPrice?: number;
    /** 建仓日期 */
    openDate?: string;
    /** 持仓状态 */
    status?: "HOLDING" | "CLOSED";
  };

  type HoldingQueryRequest = {
    /** 品种业务主键 */
    varietyKey?: string;
    /** 持仓状态 */
    status?: "HOLDING" | "CLOSED";
  };

  type HoldingUpdateRequest = {
    /** 主键 */
    id?: number;
    /** 持有份额 */
    shares?: number;
    /** 成本价 */
    costPrice?: number;
    /** 建仓日期 */
    openDate?: string;
    /** 持仓状态 */
    status?: "HOLDING" | "CLOSED";
  };

  type HoldingVO = {
    /** 主键 */
    id?: number;
    /** 所持品种的业务主键 */
    varietyKey?: string;
    /** 持有份额 */
    shares?: number;
    /** 成本价 */
    costPrice?: number;
    /** 建仓日期 */
    openDate?: string;
    /** 持仓状态 */
    status?: "HOLDING" | "CLOSED";
    /** 创建时间 */
    createTime?: string;
    /** 更新时间 */
    updateTime?: string;
  };

  type ParamQueryRequest = {
    /** 参数分组 */
    paramGroup?: "DIVIDEND" | "GRID" | "INDICATOR" | "ALERT";
    /** 参数名 */
    paramKey?: string;
  };

  type ParamSaveRequest = {
    /** 参数分组 */
    paramGroup?: "DIVIDEND" | "GRID" | "INDICATOR" | "ALERT";
    /** 参数名，组内唯一 */
    paramKey?: string;
    /** 参数值 */
    paramValue?: string;
    /** 参数说明 */
    paramDesc?: string;
  };

  type ResultBoolean = {
    code?: number;
    msg?: string;
    data?: boolean;
  };

  type ResultFundCatalogStatusVO = {
    code?: number;
    msg?: string;
    data?: FundCatalogStatusVO;
  };

  type ResultFundCatalogSyncVO = {
    code?: number;
    msg?: string;
    data?: FundCatalogSyncVO;
  };

  type ResultFundDisambiguateVO = {
    code?: number;
    msg?: string;
    data?: FundDisambiguateVO;
  };

  type ResultHoldingVO = {
    code?: number;
    msg?: string;
    /** 持仓视图对象 */
    data?: HoldingVO;
  };

  type ResultListHoldingVO = {
    code?: number;
    msg?: string;
    data?: HoldingVO[];
  };

  type ResultListStrategyParamVO = {
    code?: number;
    msg?: string;
    data?: StrategyParamVO[];
  };

  type ResultListVarietyVO = {
    code?: number;
    msg?: string;
    data?: VarietyVO[];
  };

  type ResultStrategyParamVO = {
    code?: number;
    msg?: string;
    /** 策略参数视图对象 */
    data?: StrategyParamVO;
  };

  type ResultVarietyVO = {
    code?: number;
    msg?: string;
    data?: VarietyVO;
  };

  type StrategyParamVO = {
    /** 主键 */
    id?: number;
    /** 参数分组 */
    paramGroup?: "DIVIDEND" | "GRID" | "INDICATOR" | "ALERT";
    /** 参数名 */
    paramKey?: string;
    /** 参数值 */
    paramValue?: string;
    /** 参数说明 */
    paramDesc?: string;
    /** 创建时间 */
    createTime?: string;
    /** 更新时间 */
    updateTime?: string;
  };

  type VarietyQueryRequest = {
    /** 业务主键 */
    varietyKey?: string;
    /** 6 位代码 */
    varietyCode?: string;
    /** 品种类型 */
    varietyType?: "OF" | "ETF" | "IDX";
    /** 基金类型 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 是否已确认类型 */
    confirmed?: boolean;
  };

  type VarietySaveRequest = {
    /** 业务主键，形如 OF:000001 */
    varietyKey?: string;
    /** 代码所属品种类型 */
    varietyType?: "OF" | "ETF" | "IDX";
    /** 6 位代码 */
    varietyCode?: string;
    /** 标的名称 */
    varietyName?: string;
    /** 基金类型 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 跟踪指数代码 */
    trackIndexCode?: string;
    /** 类型是否已确认 */
    confirmed?: boolean;
  };

  type VarietyVO = {
    /** 主键 */
    id?: number;
    /** 业务主键 */
    varietyKey?: string;
    /** 品种类型 */
    varietyType?: "OF" | "ETF" | "IDX";
    /** 6 位代码 */
    varietyCode?: string;
    /** 标的名称 */
    varietyName?: string;
    /** 基金类型 */
    category?:
      | "STOCK"
      | "MIXED"
      | "BOND"
      | "GOLD"
      | "DIVIDEND"
      | "ETF"
      | "UNKNOWN";
    /** 跟踪指数代码 */
    trackIndexCode?: string;
    /** 类型是否已确认 */
    confirmed?: boolean;
    /** 创建时间 */
    createTime?: string;
    /** 更新时间 */
    updateTime?: string;
  };
}
