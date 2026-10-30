<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="holding">
      <template #status>
        {{ catalog.total ? `全集 ${catalog.total} 只` : '全集未就绪' }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="overview.varietyCount ?? 0"
      :total-count="overview.varietyCount ?? 0"
      :market-mood="marketValueLabel"
      :next-note="catalog.syncTime ? `全集更新 ${shortTime(catalog.syncTime)}` : ''"
    />

    <div class="app-body">
      <AppSideRail
        section-label="持仓管理"
        :items="railItems"
        :active="activePane"
        footer-label="刷新基金全集"
        :footer-icon="ICONS.refresh"
        @select="activePane = $event"
        @footer="onSyncCatalog"
      />

      <div class="app-stage">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                持仓 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">{{ activePane === 'entry' ? '录入' : '总览' }}</b>
              </div>
              <div class="app-eyebrow">Holdings</div>
              <h1 class="app-h1">持仓录入</h1>
              <p class="app-lede">
                只输入基金或 ETF
                代码，名称、类型与最新净值自动带出。多义代码会列出候选并要求确认，确认后写入本地品种注册表。
              </p>
            </div>

            <!-- 空状态引导：尚未录入任何持仓 -->
            <div v-if="emptyState" class="app-empty">
              <div class="app-empty__ico" v-html="ICONS.compass" />
              <b>尚未录入持仓</b>
              <p>录入第一只基金或 ETF 后，总览、预估与邮件才会开始工作。输入 6 位代码开始。</p>
              <button type="button" class="app-empty__btn" @click="focusCode">开始录入</button>
            </div>

            <!-- 第一步：输入代码 -->
            <AppGroup
              v-show="activePane === 'entry'"
              title="按代码录入"
              code="ENTRY"
              :meta="disambig.degraded ? '行情降级，按全集结果展示' : ''"
              :meta-ok="!disambig.degraded"
            >
              <template #icon>
                <span v-html="ICONS.search" />
              </template>

              <AppField
                label="基金 / ETF 代码"
                description="6 位数字，如 110022、510300；输入后自动带出身份"
                :error="entryError"
              >
                <AppInput
                  ref="codeInput"
                  :model-value="code"
                  :numeric="true"
                  :invalid="Boolean(entryError)"
                  inputmode="numeric"
                  placeholder="000000"
                  class="app-code"
                  @update:model-value="onCodeInput"
                  @enter="onLookup"
                />
                <AppButton
                  variant="primary"
                  :disabled="code.length !== 6 || looking"
                  @click="onLookup"
                >
                  {{ looking ? '查询中…' : '带出身份' }}
                </AppButton>
              </AppField>

              <!-- 同码股票提示 -->
              <div v-if="disambig.stockHint" class="app-hintbar">
                同码存在股票「{{ disambig.stockHint }}」，股票不从持仓入口录入，请核对后再确认。
              </div>

              <!-- 候选列表：多义代码必须人工确认，不自动选 -->
              <div v-if="candidates.length" class="app-cands">
                <div class="app-cands__lbl">候选身份（请确认）</div>
                <button
                  v-for="candidate in candidates"
                  :key="candidate.varietyKey"
                  type="button"
                  class="app-cand"
                  :class="{
                    'app-cand--on': selected?.varietyKey === candidate.varietyKey,
                    'app-cand--off': candidate.recordable === false,
                  }"
                  :disabled="candidate.recordable === false"
                  @click="select(candidate)"
                >
                  <span class="app-cand__type">{{ candidate.varietyTypeLabel }}</span>
                  <span class="app-cand__name">{{ candidate.varietyName }}</span>
                  <span class="app-cand__cat">{{ candidate.categoryLabel }}</span>
                  <span v-if="candidate.registered" class="app-cand__reg">
                    {{
                      candidate.holdingSummary?.holding
                        ? `已持有 ${candidate.holdingSummary.totalShares} 份`
                        : '已注册'
                    }}
                  </span>
                  <span v-if="candidate.recordable === false" class="app-cand__no">不可录入</span>
                </button>
              </div>

              <!-- 相关份额：A/C 类并列展示供核对 -->
              <div v-if="related.length" class="app-cands app-cands--related">
                <div class="app-cands__lbl">名称相近的其他份额，请核对代码</div>
                <div v-for="item in related" :key="item.varietyKey" class="app-rel">
                  <span class="app-rel__code">{{ item.varietyCode }}</span>
                  <span class="app-rel__name">{{ item.varietyName }}</span>
                  <button type="button" class="app-rel__btn" @click="onPickRelated(item)">
                    改选这只
                  </button>
                </div>
              </div>

              <!-- 待确认类型：黄金 / 红利判断不确定时手动指定 -->
              <AppField
                v-if="needCategory"
                label="基金类型"
                description="该基金无法自动判定类型，请手动指定后才能录入"
                :error="categoryError"
              >
                <div class="app-types">
                  <button
                    v-for="option in CATEGORY_OPTIONS"
                    :key="option.value"
                    type="button"
                    class="app-type"
                    :class="{ 'app-type--on': manualCategory === option.value }"
                    @click="manualCategory = option.value"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </AppField>

              <AppField
                v-if="needCategory && manualCategory === 'DIVIDEND'"
                label="跟踪指数代码"
                description="红利基金必须带跟踪指数才能取股息率，如 000922"
                :error="indexError"
              >
                <AppInput
                  :model-value="manualIndex"
                  :numeric="false"
                  inputmode="text"
                  placeholder="000922"
                  @update:model-value="manualIndex = $event"
                />
              </AppField>

              <div class="app-actions">
                <AppButton
                  variant="primary"
                  size="lg"
                  :disabled="!selected || confirming"
                  @click="onConfirm"
                >
                  {{ confirming ? '确认中…' : needCategory ? '指定类型并确认' : '确认身份' }}
                </AppButton>
                <span v-if="selected" class="app-actions__picked">
                  已选：{{ selected.varietyName }}（{{ selected.varietyCode }}）
                </span>
              </div>
            </AppGroup>

            <!-- 第二步：填写份额与成本价 -->
            <AppGroup
              v-if="picked"
              title="填写持仓"
              code="SAVE"
              meta="份额与成本价必填，其他自动带出"
            >
              <template #icon>
                <span v-html="ICONS.wallet" />
              </template>

              <AppField label="已确认品种" description="名称与类型来自品种注册表，不可在此修改">
                <div class="app-picked">
                  <b>{{ picked.varietyName }}</b>
                  <span class="app-picked__meta">{{ picked.varietyKey }}</span>
                  <span v-if="pickedLatestLabel" class="app-picked__meta">{{
                    pickedLatestLabel
                  }}</span>
                </div>
              </AppField>

              <AppField label="买入份额" description="必须为正数" :error="formError">
                <AppInput
                  :model-value="shares"
                  :numeric="true"
                  :invalid="Boolean(formError)"
                  inputmode="decimal"
                  placeholder="1000"
                  @update:model-value="shares = $event"
                />
                <span class="app-unit">份</span>
              </AppField>

              <AppField label="买入成本价" description="单位净值或成交价，为 0 无法计算盈亏">
                <AppInput
                  :model-value="costPrice"
                  :numeric="true"
                  inputmode="decimal"
                  placeholder="1.5000"
                  @update:model-value="costPrice = $event"
                />
                <span class="app-unit">元/份</span>
              </AppField>

              <AppField label="建仓日期" description="留空默认今天">
                <input
                  :value="openDate"
                  type="date"
                  class="app-input app-date"
                  @input="openDate = ($event.target as HTMLInputElement).value"
                />
              </AppField>

              <div class="app-actions">
                <AppButton
                  variant="primary"
                  size="lg"
                  :saving="saving"
                  :disabled="saving"
                  @click="onSave"
                >
                  {{ saving ? '保存中…' : '保存持仓' }}
                </AppButton>
                <AppButton size="lg" @click="resetEntry">重新选代码</AppButton>
              </div>
            </AppGroup>

            <!-- 持仓总览列表 -->
            <AppGroup
              v-show="activePane === 'list'"
              title="当前持仓"
              code="LIST"
              :meta="overview.notice ?? ''"
              :meta-ok="!overview.notice"
            >
              <template #icon>
                <span v-html="ICONS.list" />
              </template>

              <div v-if="!overview.items?.length" class="app-listempty">
                还没有持有中的持仓，切到「录入」开始第一笔。
              </div>

              <div v-else class="app-rows">
                <div v-for="item in overview.items" :key="item.varietyKey" class="app-row">
                  <div class="app-row__head">
                    <div class="app-row__id">
                      <b class="app-row__name">{{ item.varietyName ?? item.varietyKey }}</b>
                      <span class="app-row__key">
                        {{ item.varietyCode }} · {{ item.categoryLabel }}
                      </span>
                      <span v-if="item.confirmed === false" class="app-row__unknown"
                        >类型待确认</span
                      >
                      <span
                        v-if="item.dividendMethod === 'REINVEST'"
                        class="app-row__unknown"
                        title="分红折成份额，不再单列现金"
                      >
                        红利再投
                      </span>
                    </div>
                    <div class="app-row__ops">
                      <AppButton size="sm" @click="openBuy(item)">买入</AppButton>
                      <AppButton size="sm" @click="openSell(item)">卖出</AppButton>
                      <AppButton size="sm" @click="openConvert(item)">转换</AppButton>
                      <AppButton size="sm" @click="showChanges(item)">变动</AppButton>
                      <AppButton size="sm" @click="openBatches(item)">改批次</AppButton>
                    </div>
                  </div>
                  <!-- 每个数字都带标签，不让人猜「+2.01」是金额还是百分比 -->
                  <div class="app-row__grid">
                    <div class="app-cell">
                      <span class="app-cell__k">总收益（含分红）</span>
                      <b class="app-cell__v" :class="toneClass(item.totalProfitLoss)">
                        {{ signedMoney(item.totalProfitLoss) }}
                      </b>
                      <span class="app-cell__sub" :class="toneClass(item.totalProfitRate)">
                        {{ signedPercent(item.totalProfitRate) }}
                      </span>
                    </div>
                    <div class="app-cell">
                      <span class="app-cell__k">今日收益</span>
                      <b class="app-cell__v" :class="toneClass(item.dailyProfitLoss)">
                        {{ signedMoney(item.dailyProfitLoss) }}
                      </b>
                      <span class="app-cell__sub" :class="toneClass(item.dailyChangePercent)">
                        {{ signedPercent(item.dailyChangePercent) }}
                      </span>
                    </div>
                    <div class="app-cell">
                      <span class="app-cell__k">市值</span>
                      <b class="app-cell__v">{{ fmtMoney(item.marketValue) }}</b>
                      <span class="app-cell__sub">{{ fmt(item.totalShares) }} 份</span>
                    </div>
                    <div class="app-cell">
                      <span class="app-cell__k">成本</span>
                      <b class="app-cell__v">{{ fmtMoney(item.costAmount) }}</b>
                      <span class="app-cell__sub">{{ fmt(item.avgCostPrice) }} 元/份</span>
                    </div>
                    <div class="app-cell">
                      <span class="app-cell__k">最新净值</span>
                      <b class="app-cell__v">{{ fmt(item.latestPrice) }}</b>
                      <span class="app-cell__sub">{{ item.latestPriceDate ?? '—' }}</span>
                    </div>
                    <div class="app-cell">
                      <span class="app-cell__k">现金分红</span>
                      <b
                        class="app-cell__v"
                        :class="item.dividendMethod === 'REINVEST' ? '' : 'app-tone-up'"
                      >
                        {{
                          item.dividendMethod === 'REINVEST'
                            ? '折份额'
                            : fmtMoney(item.dividendAmount)
                        }}
                      </b>
                      <button
                        v-if="item.dividendDetails?.length"
                        type="button"
                        class="app-cell__link"
                        title="按每笔除息发生时实际持有的份额计算，已计入总收益"
                        @click="showDividend(item)"
                      >
                        {{ item.dividendDetails.length }} 次除息
                      </button>
                      <span v-else class="app-cell__sub">无除息</span>
                    </div>
                  </div>
                </div>
                <div v-if="overview.items?.length" class="app-total">
                  成本合计 <b>{{ fmtMoney(totalCost) }}</b> <span>市值合计</span>
                  <b>{{ fmtMoney(overview.totalMarketValue) }}</b>
                  <span :class="toneClass(totalProfitLoss)">总收益</span>
                  <b :class="toneClass(totalProfitLoss)">{{ signedMoney(totalProfitLoss) }}</b>
                  <span :class="toneClass(totalProfitRate)"
                    >({{ signedPercent(totalProfitRate) }})</span
                  >
                  <span :class="toneClass(overview.totalDailyProfitLoss)">今日收益</span>
                  <b :class="toneClass(overview.totalDailyProfitLoss)">
                    {{ signedMoney(overview.totalDailyProfitLoss) }}
                  </b>
                </div>
              </div>
            </AppGroup>
          </div>
        </main>

        <aside class="app-aside">
          <AppAsideBlock title="录入进度">
            <div class="app-steps">
              <div
                v-for="(step, index) in steps"
                :key="index"
                class="app-step"
                :class="{ 'app-step--on': step.done }"
              >
                <i>{{ index + 1 }}</i>
                <span>{{ step.label }}</span>
              </div>
            </div>
          </AppAsideBlock>

          <AppAsideBlock title="最近变动">
            <template #count>
              <span v-if="changes.length" class="app-count">{{ changes.length }}</span>
            </template>
            <div class="app-changes">
              <div v-if="!changes.length" class="app-changes__empty">暂无买入卖出记录</div>
              <div v-for="change in changes" :key="change.id" class="app-change">
                <span
                  class="app-change__type"
                  :class="
                    change.changeType === 'BUY' ? 'app-change__type--buy' : 'app-change__type--sell'
                  "
                >
                  {{ change.changeType === 'BUY' ? '买入' : '卖出' }}
                </span>
                <div class="app-change__tx">
                  <b>{{ change.varietyName ?? change.varietyKey }}</b>
                  <span>{{ fmt(change.shares) }} 份 @ {{ fmt(change.unitPrice) }}</span>
                  <span v-if="change.openDate" class="app-change__bizdate"
                    >建仓 {{ change.openDate }}</span
                  >
                </div>
                <span class="app-change__time">{{ shortTime(change.createTime) }}</span>
              </div>
            </div>
          </AppAsideBlock>

          <AppAsideBlock title="数据来源">
            <div class="app-source">
              <b>{{ catalog.total ? '基金全集已就绪' : '基金全集未就绪' }}</b>
              {{
                catalog.syncTime ? `更新于 ${shortTime(catalog.syncTime)}` : '录入前请先刷新全集'
              }}
            </div>
          </AppAsideBlock>
        </aside>
      </div>
    </div>

    <!-- 查询失败弹窗：后端原因居中展示，需要使用者确认后继续 -->
    <div v-if="lookupError" class="app-mask" @click.self="lookupError = ''">
      <div class="app-dialog">
        <h3>未能带出身份</h3>
        <p class="app-dialog__meta">{{ lookupError }}</p>
        <div class="app-dialog__ops">
          <AppButton variant="primary" @click="lookupError = ''">知道了</AppButton>
        </div>
      </div>
    </div>

    <!-- 卖出弹层 -->
    <div v-if="selling" class="app-mask" @click.self="selling = null">
      <div class="app-dialog">
        <h3>卖出 {{ selling.varietyName }}</h3>
        <p class="app-dialog__meta">
          当前持有 {{ fmt(selling.totalShares) }} 份，按先进先出扣减，历史逐笔保留。
        </p>
        <AppField label="卖出份额" description="不超过当前持有合计" :error="sellError">
          <AppInput
            :model-value="sellShares"
            :numeric="true"
            :invalid="Boolean(sellError)"
            inputmode="decimal"
            @update:model-value="sellShares = $event"
          />
          <span class="app-unit">份</span>
        </AppField>
        <div class="app-dialog__ops">
          <AppButton variant="primary" :disabled="!sellShares" @click="onSell">确认卖出</AppButton>
          <AppButton @click="selling = null">取消</AppButton>
        </div>
      </div>
    </div>

    <!-- 加仓弹层 -->
    <div v-if="buying" class="app-mask" @click.self="buying = null">
      <div class="app-dialog">
        <h3>加仓 {{ buying.varietyName }}</h3>
        <p class="app-dialog__meta">
          当前持有 {{ fmt(buying.totalShares) }} 份，加权成本 {{ fmt(buying.avgCostPrice) }}。
          这里只补充份额与成本，名称与类型仍来自品种注册表。
        </p>
        <AppField label="买入份额" description="必须为正数" :error="buyError">
          <AppInput
            :model-value="buyShares"
            :numeric="true"
            :invalid="Boolean(buyError)"
            inputmode="decimal"
            placeholder="100"
            @update:model-value="buyShares = $event"
          />
          <span class="app-unit">份</span>
        </AppField>
        <AppField label="买入成本价" description="成交价或当日净值" :error="buyError">
          <AppInput
            :model-value="buyCostPrice"
            :numeric="true"
            :invalid="Boolean(buyError)"
            inputmode="decimal"
            placeholder="1.5000"
            @update:model-value="buyCostPrice = $event"
          />
          <span class="app-unit">元/份</span>
        </AppField>
        <div class="app-dialog__ops">
          <AppButton variant="primary" @click="onBuy">确认加仓</AppButton>
          <AppButton @click="buying = null">取消</AppButton>
        </div>
      </div>
    </div>

    <!-- 转换弹层 -->
    <div v-if="converting" class="app-mask" @click.self="converting = null">
      <div class="app-dialog app-dialog--wide">
        <h3>转换 {{ converting.varietyName }}</h3>
        <p class="app-dialog__meta">
          一次提交同时完成转出与转入，任一步失败整笔回滚，不会出现「已卖未买」。
          转出按先进先出扣减，转入份额与成本价按实际成交填写。
        </p>
        <AppField label="转出份额" description="不超过当前持有合计" :error="convertError">
          <AppInput
            :model-value="convertShares"
            :numeric="true"
            :invalid="Boolean(convertError)"
            inputmode="decimal"
            :placeholder="fmt(converting.totalShares)"
            @update:model-value="convertShares = $event"
          />
          <span class="app-unit">份</span>
        </AppField>
        <AppField label="转入基金" description="从当前持仓里选，不含转出这一只">
          <select
            class="app-input app-select"
            :value="convertTarget"
            @change="onConvertTargetChange(($event.target as HTMLSelectElement).value)"
          >
            <option value="">请选择转入基金</option>
            <option v-for="row in convertTargets" :key="row.varietyKey" :value="row.varietyKey">
              {{ row.varietyName }}（{{ row.varietyCode }}）
            </option>
          </select>
        </AppField>
        <AppField label="转入份额" description="按实际成交份额填写">
          <AppInput
            :model-value="convertToShares"
            :numeric="true"
            inputmode="decimal"
            placeholder="100"
            @update:model-value="convertToShares = $event"
          />
          <span class="app-unit">份</span>
        </AppField>
        <AppField label="转入成本价" description="转入基金的成交净值">
          <AppInput
            :model-value="convertToCost"
            :numeric="true"
            inputmode="decimal"
            placeholder="1.5000"
            @update:model-value="convertToCost = $event"
          />
          <span class="app-unit">元/份</span>
        </AppField>
        <div class="app-dialog__ops">
          <AppButton variant="primary" @click="onConvert">确认转换</AppButton>
          <AppButton @click="converting = null">取消</AppButton>
        </div>
      </div>
    </div>

    <!-- 变动历史弹层 -->
    <div v-if="historyOf" class="app-mask" @click.self="historyOf = null">
      <div class="app-dialog app-dialog--wide">
        <h3>{{ historyOf.varietyName }} 的变动历史</h3>
        <div class="app-history">
          <div v-if="!historyRows.length" class="app-changes__empty">暂无记录</div>
          <div v-for="row in historyRows" :key="row.id" class="app-change">
            <span
              class="app-change__type"
              :class="row.changeType === 'BUY' ? 'app-change__type--buy' : 'app-change__type--sell'"
            >
              {{ row.changeType === 'BUY' ? '买入' : '卖出' }}
            </span>
            <div class="app-change__tx">
              <b>{{ fmt(row.shares) }} 份 @ {{ fmt(row.unitPrice) }}</b>
              <span class="app-change__note">{{ row.note }}</span>
            </div>
            <span class="app-change__time">{{ shortTime(row.createTime) }}</span>
          </div>
        </div>
        <div class="app-dialog__ops">
          <AppButton @click="historyOf = null">关闭</AppButton>
        </div>
      </div>
    </div>

    <!-- 批次更正弹层：录错了要能改，改完在变动历史里留一条更正说明 -->
    <div v-if="batchOf" class="app-mask" @click.self="batchOf = null">
      <div class="app-dialog app-dialog--wide">
        <h3>{{ batchOf.varietyName }} 的持仓批次</h3>
        <p class="app-dialog__meta">
          这部分是录入时的原始记录。份额或成本价填错了可直接改，「撤销」只对没卖出过的批次开放；
          改动会追加一条更正说明到变动历史，旧记录不删。分红方式选错会让现金分红差很多。
        </p>
        <AppField label="分红方式" description="红利再投时分红已折成份额，现金分红按 0 计">
          <div class="app-types">
            <button
              v-for="option in DIVIDEND_METHODS"
              :key="option.value"
              type="button"
              class="app-type"
              :class="{ 'app-type--on': batchOf.dividendMethod === option.value }"
              @click="onChangeDividendMethod(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </AppField>
        <div v-if="!batchOf.batches?.length" class="app-changes__empty">没有持有中的批次</div>
        <div v-else class="app-batches">
          <div v-for="batch in batchOf.batches" :key="batch.id" class="app-batch">
            <div v-if="editingBatchId === batch.id" class="app-batch__form">
              <AppField label="份额" :error="batchError">
                <AppInput
                  :model-value="batchDraft.shares"
                  :numeric="true"
                  inputmode="decimal"
                  @update:model-value="updateBatchDraft('shares', $event)"
                />
                <span class="app-unit">份</span>
              </AppField>
              <AppField label="成本价">
                <AppInput
                  :model-value="batchDraft.costPrice"
                  :numeric="true"
                  inputmode="decimal"
                  @update:model-value="updateBatchDraft('costPrice', $event)"
                />
                <span class="app-unit">元/份</span>
              </AppField>
              <AppField label="建仓日期">
                <input
                  :value="batchDraft.openDate"
                  type="date"
                  class="app-input app-date"
                  @input="updateBatchDraft('openDate', ($event.target as HTMLInputElement).value)"
                />
              </AppField>
              <div class="app-dialog__ops">
                <AppButton variant="primary" :disabled="batchSaving" @click="onSaveBatch">
                  {{ batchSaving ? '保存中…' : '保存更正' }}
                </AppButton>
                <AppButton @click="editingBatchId = null">取消</AppButton>
              </div>
            </div>
            <div v-else class="app-batch__row">
              <span class="app-batch__shares">{{ fmt(batch.shares) }} 份</span>
              <span class="app-batch__cost">{{ fmt(batch.costPrice) }} 元/份</span>
              <span class="app-batch__money">成本 {{ fmtMoney(batch.costAmount) }} 元</span>
              <span class="app-batch__date">建仓 {{ batch.openDate ?? '—' }}</span>
              <span class="app-batch__ops">
                <AppButton size="sm" @click="startEditBatch(batch)">更正</AppButton>
                <AppButton size="sm" @click="onRevokeBatch(batch)">撤销</AppButton>
              </span>
            </div>
          </div>
        </div>
        <div class="app-dialog__ops">
          <AppButton @click="batchOf = null">关闭</AppButton>
        </div>
      </div>
    </div>

    <!-- 分红明细弹层：逐笔列出除息日与每份金额，供与平台核对 -->
    <div v-if="dividendOf" class="app-mask" @click.self="dividendOf = null">
      <div class="app-dialog">
        <h3>{{ dividendOf.varietyName }} 的除息明细</h3>
        <p class="app-dialog__meta">{{ dividendOf.dividendNote ?? '' }}</p>
        <div class="app-history">
          <div v-for="row in dividendOf.dividendDetails ?? []" :key="row.date" class="app-change">
            <span class="app-change__type app-change__type--buy">{{ row.date }}</span>
            <div class="app-change__tx">
              <b>每份 {{ fmt(row.perShare) }} 元</b>
              <span class="app-change__note"
                >除息日持有 {{ fmt(row.shares) }} 份，计 {{ fmtMoney(row.amount) }} 元</span
              >
            </div>
          </div>
        </div>
        <div class="app-dialog__ops">
          <AppButton @click="dividendOf = null">关闭</AppButton>
        </div>
      </div>
    </div>

    <MessageHost />
  </div>
</template>

<script setup lang="ts">
/**
 * 持仓录入页：输入代码带出身份、候选确认、份额成本保存、卖出与变动历史
 */
import { computed, onMounted, ref } from 'vue'
import AppButton from '@/ui/AppButton.vue'
import AppField from '@/ui/AppField.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppInput from '@/ui/AppInput.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppSideRail from '@/layout/AppSideRail.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import AppAsideBlock from '@/layout/AppAsideBlock.vue'
import MessageHost from '@/ui/MessageHost.vue'
import { ElMessage } from '@/ui/message'
import {
  buy as buyHolding,
  changes as holdingChanges,
  convert as convertHolding,
  overview as holdingOverview,
  revokeBatch as revokeBatchApi,
  sell as sellHolding,
  updateBatch as updateBatchApi,
} from '@/api/holdingController'
import { save as saveVariety } from '@/api/varietyController'
import {
  catalogStatus as catalogStatusApi,
  confirm as confirmCandidate,
  disambiguate as disambiguateApi,
  syncCatalog as syncCatalogApi,
} from '@/api/fundController'

/** 分组图标，内联 SVG 保持与全站一致 */
const ICONS: Record<string, string> = {
  search:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/></svg>',
  wallet:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="6.5" width="18" height="13" rx="2.5"/><path d="M3 10h18M16 15h2"/></svg>',
  list: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01"/></svg>',
  refresh:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 1 0-2.3 6.3"/><path d="M20 5v6h-6"/></svg>',
  compass:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8.6"/><path d="m15.2 8.8-2 4.4-4.4 2 2-4.4z"/></svg>',
}

/** 分红方式选项，红利再投时分红已折成份额，不能再算一遍现金 */
const DIVIDEND_METHODS = [
  { value: 'CASH', label: '现金分红' },
  { value: 'REINVEST', label: '红利再投' },
] as const

/** 手动指定的类型选项，只给 PRD 定义的四类 */
const CATEGORY_OPTIONS = [
  { value: 'STOCK', label: '股票型' },
  { value: 'MIXED', label: '混合型' },
  { value: 'BOND', label: '债券型' },
  { value: 'GOLD', label: '黄金' },
  { value: 'DIVIDEND', label: '红利' },
]

/** 侧栏锚点 */
const railItems = [
  { key: 'entry', label: '录入持仓', icon: ICONS.search! },
  { key: 'list', label: '当前持仓', icon: ICONS.list! },
]

/** 当前侧栏页 */
const activePane = ref('entry')

/** 输入中的代码 */
const code = ref('')

/** 消歧中 */
const looking = ref(false)

/** 消歧结果 */
const disambig = ref<Partial<API.FundDisambiguateVO>>({})

/** 当前选中的候选 */
const selected = ref<API.FundCandidateVO | null>(null)

/** 确认后的品种 */
const picked = ref<{ varietyKey: string; varietyName: string; code: string } | null>(null)

/** 确认中 */
const confirming = ref(false)

/** 手动指定的类型与指数 */
const manualCategory = ref('')
const manualIndex = ref('')

/** 表单值 */
const shares = ref('')
const costPrice = ref('')
const openDate = ref('')

/** 保存中 */
const saving = ref(false)

/** 各处错误文案 */
const entryError = ref('')
const categoryError = ref('')
const indexError = ref('')
const formError = ref('')
const sellError = ref('')

/** 查询失败弹窗文案，空字符串表示不显示 */
const lookupError = ref('')

/** 基金全集状态 */
const catalog = ref<API.FundCatalogStatusVO>({})

/**
 * 持仓总览
 *
 * `totalProfitLoss` / `totalProfitRate` / `dividendDetails[].shares` 是后端新增的含分红口径字段，
 * Apifox 尚未重新生成，故在此声明；生成后删掉这些可选字段即可。
 */
type HoldingVarietyDisplay = API.HoldingVarietyVO & {
  /** 含现金分红的总收益，与支付宝「持有收益」同口径 */
  totalProfitLoss?: number
  /** 含现金分红的总收益率，百分点 */
  totalProfitRate?: number
  /** 逐笔除息明细，额外带出每笔计入的份额 */
  dividendDetails?: Array<API.HoldingDividendVO & { shares?: number }>
}

type HoldingOverviewDisplay = Omit<API.HoldingOverviewVO, 'items'> & {
  items?: HoldingVarietyDisplay[]
  /** 含分红的总收益合计 */
  totalProfitLoss?: number
  /** 含分红的总收益率合计，百分点 */
  totalProfitRate?: number
  /** 成本合计 */
  totalCostAmount?: number
}

const overview = ref<HoldingOverviewDisplay>({})

/** 最近变动历史 */
const changes = ref<API.HoldingChangeVO[]>([])

/** 卖出弹层状态 */
const selling = ref<HoldingVarietyDisplay | null>(null)
const sellShares = ref('')

/** 加仓弹层状态 */
const buying = ref<HoldingVarietyDisplay | null>(null)
const buyShares = ref('')
const buyCostPrice = ref('')
const buyError = ref('')

/** 转换弹层状态 */
const converting = ref<HoldingVarietyDisplay | null>(null)
const convertShares = ref('')
const convertTarget = ref('')
const convertToShares = ref('')
const convertToCost = ref('')
const convertError = ref('')

/** 可转入的品种，供转换弹层选择 */
const convertTargets = computed(() =>
  (overview.value.items ?? []).filter((item) => item.varietyKey !== converting.value?.varietyKey),
)

/** 变动历史弹层状态 */
const historyOf = ref<API.HoldingVarietyVO | null>(null)
const historyRows = ref<API.HoldingChangeVO[]>([])

/** 批次更正弹层状态 */
const batchOf = ref<HoldingVarietyDisplay | null>(null)
const editingBatchId = ref<number | null>(null)
const batchDraft = ref({ shares: '', costPrice: '', openDate: '' })
const batchError = ref('')
const batchSaving = ref(false)

/** 分红明细弹层状态 */
const dividendOf = ref<HoldingVarietyDisplay | null>(null)

/** 代码输入框引用 */
const codeInput = ref<InstanceType<typeof AppInput> | null>(null)

/** 可录入的候选 */
const candidates = computed(() =>
  (disambig.value.candidates ?? []).filter(
    (item: API.FundCandidateVO) => item.recordable !== false,
  ),
)

/** 名称相近的份额 */
const related = computed(() => disambig.value.relatedShareClasses ?? [])

/** 是否需要手动指定类型 */
const needCategory = computed(() => Boolean(selected.value && selected.value.confirmed === false))

/** 页面是否处于空状态：无持仓且未开始录入 */
const emptyState = computed(
  () => (overview.value.varietyCount ?? 0) === 0 && !picked.value && candidates.value.length === 0,
)

/** 录入步骤进度 */
const steps = computed(() => [
  { label: '输入 6 位代码', done: code.value.length === 6 },
  { label: '确认候选身份', done: Boolean(picked.value) },
  { label: '填写份额与成本', done: Boolean(shares.value && costPrice.value) },
])

/** 成本合计，用后端汇总值，缺省时按逐只累加兜底 */
const totalCost = computed(
  () =>
    overview.value.totalCostAmount ??
    (overview.value.items ?? []).reduce((sum, item) => sum + (item.costAmount ?? 0), 0),
)

/** 含分红的总收益合计，与支付宝「持有收益」同口径 */
const totalProfitLoss = computed(
  () =>
    overview.value.totalProfitLoss ??
    (overview.value.items ?? []).reduce((sum, item) => sum + (item.totalProfitLoss ?? 0), 0),
)

const totalProfitRate = computed(() =>
  overview.value.totalProfitRate != null
    ? overview.value.totalProfitRate
    : totalCost.value > 0
      ? (totalProfitLoss.value / totalCost.value) * 100
      : 0,
)

/** 市值摘要，用于行情条 */
const marketValueLabel = computed(() => {
  const items = overview.value.items ?? []
  if (!items.length) {
    return '—'
  }
  const day = overview.value.totalDailyProfitLoss
  const base = `市值 ${fmtMoney(overview.value.totalMarketValue)}`
  if (day == null) {
    return base
  }
  return `${base} · 今日 ${day > 0 ? '+' : ''}${fmtMoney(day)}`
})

/** 已确认品种的最新价说明 */
const pickedLatestLabel = computed(() => {
  const item = overview.value.items?.find((row) => row.varietyKey === picked.value?.varietyKey)
  if (!item?.latestPrice) {
    return ''
  }
  return `${item.priceSource ?? '最新价'} ${fmt(item.latestPrice)}`
})

/** 金额格式化 */
function fmt(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

/** 两位小数金额 */
function fmtMoney(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** 带正负号的金额 */
function signedMoney(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return `${value > 0 ? '+' : ''}${fmtMoney(value)}`
}

/** 带正负号的百分比 */
function signedPercent(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return `${value > 0 ? '+' : ''}${Number(value).toFixed(2)}%`
}

/** 根据盈亏方向返回涨跌颜色 */
function toneClass(value?: number | null): string {
  if (value === undefined || value === null || value === 0) {
    return 'app-tone-flat'
  }
  return value > 0 ? 'app-tone-up' : 'app-tone-down'
}

/** 时间截短到时分 */
function shortTime(value?: string | null): string {
  if (!value) {
    return ''
  }
  return value.replace('T', ' ').slice(5, 16)
}

/** 代码输入：只留数字并清空上一轮结果 */
function onCodeInput(value: string): void {
  code.value = value.replace(/\D/g, '').slice(0, 6)
  entryError.value = ''
  if (value.length !== 6) {
    disambig.value = {}
    selected.value = null
  }
}

/** 查询代码身份 */
async function onLookup(): Promise<void> {
  if (code.value.length !== 6) {
    entryError.value = '代码必须是 6 位数字'
    return
  }
  looking.value = true
  entryError.value = ''
  lookupError.value = ''
  selected.value = null
  picked.value = null
  manualCategory.value = ''
  manualIndex.value = ''
  try {
    const result = await disambiguateApi({ code: code.value }, { silent: true })
    disambig.value = result.data ?? {}
    const list = disambig.value.candidates ?? []
    if (!list.some((item: API.FundCandidateVO) => item.recordable !== false)) {
      entryError.value = disambig.value.stockHint
        ? `该代码是股票「${disambig.value.stockHint}」，不从持仓入口录入`
        : '未找到可录入的基金或 ETF'
    }
  } catch (error) {
    // 查不到代码属于使用者需要面对的结果，用居中弹窗展示后端原因而不是右下角浮层
    lookupError.value =
      error instanceof Error && error.message ? error.message : '查询失败，请稍后再试'
  } finally {
    looking.value = false
  }
}

/** 选中一个候选 */
function select(candidate: API.FundCandidateVO): void {
  selected.value = candidate
  categoryError.value = ''
  indexError.value = ''
}

/** 从相关份额改选 */
function onPickRelated(item: API.FundCandidateVO): void {
  if (!item.varietyCode) {
    return
  }
  code.value = item.varietyCode
  void onLookup()
}

/** 确认身份并写入注册表 */
async function onConfirm(): Promise<void> {
  const candidate = selected.value
  if (!candidate?.varietyCode || !candidate.varietyType) {
    entryError.value = '请先选择一个候选身份'
    return
  }
  if (candidate.confirmed === false && !manualCategory.value) {
    categoryError.value = '该基金类型无法自动判定，请手动指定'
    return
  }
  if (manualCategory.value === 'DIVIDEND' && !manualIndex.value.trim()) {
    indexError.value = '红利基金必须指定跟踪指数'
    return
  }
  confirming.value = true
  try {
    const result = await confirmCandidate({
      code: candidate.varietyCode,
      varietyType: candidate.varietyType === 'ETF' ? 'ETF' : 'OF',
      ...(manualCategory.value
        ? { category: manualCategory.value as API.FundConfirmRequest['category'] }
        : {}),
      ...(manualCategory.value === 'DIVIDEND' ? { trackIndexCode: manualIndex.value.trim() } : {}),
    })
    const variety = result.data ?? {}
    picked.value = {
      varietyKey: variety.varietyKey ?? candidate.varietyKey ?? '',
      varietyName: variety.varietyName ?? candidate.varietyName ?? '',
      code: candidate.varietyCode,
    }
    ElMessage.success(`已确认 ${picked.value.varietyName}，请填写份额与成本价`)
    activePane.value = 'entry'
  } catch {
    // request 层已弹出错误
  } finally {
    confirming.value = false
  }
}

/** 保存持仓 */
async function onSave(): Promise<void> {
  if (!picked.value) {
    return
  }
  const sharesValue = Number(shares.value)
  const costValue = Number(costPrice.value)
  if (!Number.isFinite(sharesValue) || sharesValue <= 0) {
    formError.value = '份额必须为正数'
    return
  }
  formError.value = ''
  if (!Number.isFinite(costValue) || costValue <= 0) {
    ElMessage.error('成本价必须为正数，为 0 无法计算盈亏')
    return
  }
  saving.value = true
  try {
    await buyHolding({
      varietyKey: picked.value.varietyKey,
      shares: sharesValue,
      costPrice: costValue,
      ...(openDate.value.trim() ? { openDate: openDate.value.trim() } : {}),
    })
    ElMessage.success(`已保存 ${picked.value.varietyName} ${sharesValue} 份`)
    resetEntry()
    await loadAll()
    activePane.value = 'list'
  } catch {
    // request 层已弹出具体原因
  } finally {
    saving.value = false
  }
}

/** 打开卖出弹层 */
function openSell(item: API.HoldingVarietyVO): void {
  selling.value = item
  sellShares.value = ''
  sellError.value = ''
}

/** 打开加仓弹层，默认带上当前净值与成本价便于快速加仓 */
function openBuy(item: HoldingVarietyDisplay): void {
  buying.value = item
  buyShares.value = ''
  buyCostPrice.value = item.latestPrice != null ? String(item.latestPrice) : ''
  buyError.value = ''
}

/** 提交加仓 */
async function onBuy(): Promise<void> {
  if (!buying.value?.varietyKey) {
    return
  }
  const sharesValue = Number(buyShares.value)
  const costValue = Number(buyCostPrice.value)
  if (!Number.isFinite(sharesValue) || sharesValue <= 0) {
    buyError.value = '买入份额必须为正数'
    return
  }
  if (!Number.isFinite(costValue) || costValue <= 0) {
    buyError.value = '买入成本价必须为正数'
    return
  }
  buyError.value = ''
  try {
    await buyHolding({
      varietyKey: buying.value.varietyKey,
      shares: sharesValue,
      costPrice: costValue,
    })
    ElMessage.success(`已加仓 ${buying.value.varietyName} ${sharesValue} 份`)
    buying.value = null
    await loadAll()
  } catch {
    // request 层已弹出具体原因
  }
}

/** 打开转换弹层 */
function openConvert(item: HoldingVarietyDisplay): void {
  converting.value = item
  convertShares.value = ''
  convertTarget.value = ''
  convertToShares.value = ''
  convertToCost.value = ''
  convertError.value = ''
}

/** 选中转入品种时带出它的最新净值作为成本价默认值 */
function onConvertTargetChange(value: string): void {
  convertTarget.value = value
  const target = convertTargets.value.find((item) => item.varietyKey === value)
  convertToCost.value = target?.latestPrice != null ? String(target.latestPrice) : ''
}

/** 提交转换，后端同一事务内先卖出再买入 */
async function onConvert(): Promise<void> {
  const source = converting.value
  if (!source?.varietyKey || !convertTarget.value) {
    convertError.value = '请选择要转入的基金'
    return
  }
  const outShares = Number(convertShares.value)
  const inShares = Number(convertToShares.value)
  const inCost = Number(convertToCost.value)
  if (!Number.isFinite(outShares) || outShares <= 0) {
    convertError.value = '转出份额必须为正数'
    return
  }
  if (source.totalShares != null && outShares > source.totalShares) {
    convertError.value = `转出份额超过当前持有 ${fmt(source.totalShares)} 份`
    return
  }
  if (!Number.isFinite(inShares) || inShares <= 0) {
    convertError.value = '转入份额必须为正数'
    return
  }
  if (!Number.isFinite(inCost) || inCost <= 0) {
    convertError.value = '转入成本价必须为正数'
    return
  }
  convertError.value = ''
  try {
    const result = await convertHolding({
      fromVarietyKey: source.varietyKey,
      toVarietyKey: convertTarget.value,
      shares: outShares,
      toShares: inShares,
      toCostPrice: inCost,
    })
    ElMessage.success(result.data?.note ?? '转换已完成')
    converting.value = null
    await loadAll()
  } catch {
    // request 层已弹出具体原因
  }
}

/** 提交卖出 */
async function onSell(): Promise<void> {
  if (!selling.value?.varietyKey) {
    return
  }
  const value = Number(sellShares.value)
  if (!Number.isFinite(value) || value <= 0) {
    sellError.value = '卖出份额必须为正数'
    return
  }
  sellError.value = ''
  try {
    await sellHolding({ varietyKey: selling.value.varietyKey, shares: value })
    ElMessage.success(`已卖出 ${selling.value.varietyName} ${value} 份`)
    selling.value = null
    await loadAll()
  } catch {
    // request 层已弹出具体原因
  }
}

/** 查看单品种变动历史 */
async function showChanges(item: API.HoldingVarietyVO): Promise<void> {
  historyOf.value = item
  try {
    const result = await holdingChanges({ varietyKey: item.varietyKey })
    historyRows.value = result.data ?? []
  } catch {
    historyRows.value = []
  }
}

/** 刷新基金全集 */
async function onSyncCatalog(): Promise<void> {
  try {
    const result = await syncCatalogApi()
    ElMessage.success(`基金全集已刷新，共 ${result.data?.total ?? 0} 只`)
    const status = await catalogStatusApi()
    catalog.value = status.data ?? {}
  } catch {
    // request 层已弹出错误
  }
}

/** 回到录入起点 */
function resetEntry(): void {
  code.value = ''
  disambig.value = {}
  selected.value = null
  picked.value = null
  manualCategory.value = ''
  manualIndex.value = ''
  shares.value = ''
  costPrice.value = ''
  openDate.value = ''
  entryError.value = ''
  formError.value = ''
}

/** 聚焦代码输入框 */
function focusCode(): void {
  activePane.value = 'entry'
  const element = document.querySelector<HTMLInputElement>('input.app-code')
  element?.focus()
}

/** 拉取页面全部数据 */
async function loadAll(): Promise<void> {
  const [statusResult, overviewResult, changesResult] = await Promise.allSettled([
    catalogStatusApi(),
    holdingOverview(),
    holdingChanges({}),
  ])
  if (statusResult.status === 'fulfilled') {
    catalog.value = statusResult.value.data ?? {}
  }
  if (overviewResult.status === 'fulfilled') {
    overview.value = overviewResult.value.data ?? {}
  }
  if (changesResult.status === 'fulfilled') {
    changes.value = (changesResult.value.data ?? []).slice(0, 8)
  }
}

/** 打开批次更正弹层 */
function openBatches(item: HoldingVarietyDisplay): void {
  batchOf.value = item
  editingBatchId.value = null
  batchError.value = ''
}

/** 进入某个批次的编辑态，把当前值带进表单 */
function startEditBatch(batch: API.HoldingBatchVO): void {
  if (batch.id == null) {
    return
  }
  editingBatchId.value = batch.id
  batchDraft.value = {
    shares: batch.shares != null ? String(batch.shares) : '',
    costPrice: batch.costPrice != null ? String(batch.costPrice) : '',
    openDate: batch.openDate ?? '',
  }
  batchError.value = ''
}

/** 更新批次表单的某个字段 */
function updateBatchDraft(field: 'shares' | 'costPrice' | 'openDate', value: string): void {
  batchDraft.value = { ...batchDraft.value, [field]: value }
  batchError.value = ''
}

/** 提交批次更正 */
async function onSaveBatch(): Promise<void> {
  const id = editingBatchId.value
  if (id == null) {
    return
  }
  const sharesValue = Number(batchDraft.value.shares)
  const costValue = Number(batchDraft.value.costPrice)
  if (!Number.isFinite(sharesValue) || sharesValue <= 0) {
    batchError.value = '份额必须为正数'
    return
  }
  if (!Number.isFinite(costValue) || costValue <= 0) {
    batchError.value = '成本价必须为正数'
    return
  }
  batchSaving.value = true
  try {
    await updateBatchApi({
      id,
      shares: sharesValue,
      costPrice: costValue,
      ...(batchDraft.value.openDate ? { openDate: batchDraft.value.openDate } : {}),
    })
    ElMessage.success('已更正，变动历史里追加了一条说明')
    editingBatchId.value = null
    batchOf.value = null
    await loadAll()
  } catch {
    // request 层已弹出具体原因
  } finally {
    batchSaving.value = false
  }
}

/** 撤销一个批次，后端会拒绝已卖出过的批次 */
async function onRevokeBatch(batch: API.HoldingBatchVO): Promise<void> {
  if (batch.id == null) {
    return
  }
  try {
    await revokeBatchApi({ id: batch.id })
    ElMessage.success('已撤销该批次')
    batchOf.value = null
    await loadAll()
  } catch {
    // request 层已弹出具体原因
  }
}

/** 打开分红明细弹层 */
function showDividend(item: HoldingVarietyDisplay): void {
  dividendOf.value = item
}

/** 切换分红方式，红利再投时分红已折成份额，现金分红不能再算一遍 */
async function onChangeDividendMethod(method: 'CASH' | 'REINVEST'): Promise<void> {
  const item = batchOf.value
  if (!item?.varietyKey || item.dividendMethod === method) {
    return
  }
  try {
    // 品种保存是按业务主键覆盖，未传的字段会被写成 null，故把已知身份字段一并回写
    await saveVariety({
      varietyKey: item.varietyKey,
      dividendMethod: method,
      ...(item.varietyCode ? { varietyCode: item.varietyCode } : {}),
      ...(item.varietyType ? { varietyType: item.varietyType } : {}),
      ...(item.varietyName ? { varietyName: item.varietyName } : {}),
      ...(item.category ? { category: item.category } : {}),
      ...(item.confirmed != null ? { confirmed: item.confirmed } : {}),
    })
    ElMessage.success(method === 'REINVEST' ? '已改为红利再投，现金分红按 0 计' : '已改为现金分红')
    batchOf.value = { ...item, dividendMethod: method }
    await loadAll()
  } catch {
    // request 层已弹出具体原因
  }
}

onMounted(() => {
  void loadAll()
})
</script>

<style scoped>
.app-page {
  min-height: 100vh;
  background: var(--canvas);
  transition: background 0.3s;
}

.app-atmos {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.app-atmos__a,
.app-atmos__b {
  position: absolute;
  filter: blur(22px);
}

.app-atmos__a {
  top: -320px;
  left: -128px;
  width: 960px;
  height: 540px;
  background: radial-gradient(ellipse at center, var(--atmos-a), transparent 72%);
}

.app-atmos__b {
  top: -300px;
  right: -200px;
  width: 840px;
  height: 520px;
  background: radial-gradient(ellipse at center, var(--atmos-b), transparent 72%);
}

.app-body {
  position: relative;
  z-index: 2;
  padding-top: calc(var(--topbar) + var(--pulse));
  padding-left: var(--rail);
  transition: padding-left 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

.app-stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--aside);
  align-items: start;
}

.app-center {
  min-width: 0;
  padding: 26px 26px 60px;
}

.app-center__in {
  max-width: var(--content);
  margin: 0 auto;
}

.app-aside {
  align-self: stretch;
  min-height: calc(100vh - var(--topbar) - var(--pulse));
  padding: 26px 18px 60px;
  border-left: 1px solid var(--hair);
  background: var(--aside-bg);
  transition: background 0.3s;
}

@media (max-width: 1180px) {
  .app-stage {
    grid-template-columns: minmax(0, 1fr);
  }

  .app-aside {
    display: none;
  }
}

@media (max-width: 860px) {
  .app-body {
    padding-top: var(--topbar);
    padding-left: 0;
  }

  .app-center {
    padding: 20px 16px 48px;
  }
}

/* ── 页面头部 ── */
.app-head {
  margin-bottom: 24px;
}

.app-crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--t4);
}

.app-crumb__sl {
  opacity: 0.55;
}

.app-crumb__b {
  font-weight: 500;
  color: var(--t3);
}

.app-eyebrow {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 5px;
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
  color: var(--acc);
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
}

.app-h1 {
  margin-top: 11px;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.18;
  letter-spacing: -0.033em;
}

.app-lede {
  max-width: 600px;
  margin-top: 9px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--t3);
}

/* ── 空状态 ── */
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  padding: 34px 24px;
  border: 1px dashed var(--hair-2);
  border-radius: 15px;
  background: var(--s1);
  text-align: center;
}

.app-empty__ico {
  width: 44px;
  height: 44px;
  color: var(--acc);
}

.app-empty__ico :deep(svg) {
  width: 100%;
  height: 100%;
  stroke: currentColor;
  fill: none;
}

.app-empty b {
  font-size: 15px;
  font-weight: 650;
  color: var(--t1);
}

.app-empty p {
  max-width: 380px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--t3);
}

.app-empty__btn {
  height: 34px;
  padding: 0 18px;
  border: 0;
  border-radius: 7px;
  background: linear-gradient(180deg, var(--acc-2), var(--acc));
  color: var(--acc-on);
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.app-empty__btn:hover {
  filter: brightness(1.05);
}

/* ── 提示条 ── */
.app-hintbar {
  margin: 4px 0 10px;
  padding: 10px 14px;
  border-radius: 9px;
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
  color: var(--acc);
  font-size: 11.5px;
  line-height: 1.6;
}

/* ── 候选列表 ── */
.app-cands {
  margin: 6px 0 12px;
}

.app-cands__lbl {
  margin-bottom: 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--t4);
}

.app-cand {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-bottom: 6px;
  padding: 11px 14px;
  border: 1px solid var(--hair-2);
  border-radius: 10px;
  background: var(--inp-bg);
  text-align: left;
  font: inherit;
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.app-cand:hover {
  border-color: var(--hair-3);
}

.app-cand--on {
  border-color: var(--acc);
  box-shadow: 0 0 0 3px var(--acc-glow);
}

.app-cand--off {
  opacity: 0.55;
  cursor: not-allowed;
}

.app-cand__type {
  flex: none;
  padding: 2px 7px;
  border-radius: 4px;
  background: var(--s3);
  color: var(--t2);
  font-size: 10.5px;
  font-weight: 600;
}

.app-cand__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  font-weight: 550;
  color: var(--t1);
}

.app-cand__cat {
  flex: none;
  font-size: 11px;
  color: var(--t3);
}

.app-cand__reg {
  flex: none;
  padding: 1.5px 7px;
  border-radius: 4px;
  background: var(--dn-soft);
  color: var(--dn);
  font-size: 10px;
  font-weight: 650;
}

.app-cand__no {
  flex: none;
  padding: 1.5px 7px;
  border-radius: 4px;
  background: var(--acc-soft);
  color: var(--acc);
  font-size: 10px;
  font-weight: 650;
}

/* ── 相关份额 ── */
.app-cands--related {
  padding-top: 8px;
  border-top: 1px solid var(--hair);
}

.app-rel {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  font-size: 12px;
}

.app-rel__code {
  flex: none;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--t2);
}

.app-rel__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--t2);
}

.app-rel__btn {
  flex: none;
  height: 24px;
  padding: 0 9px;
  border: 1px solid var(--hair-2);
  border-radius: 6px;
  background: var(--btn-bg);
  color: var(--t2);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.app-rel__btn:hover {
  border-color: var(--hair-3);
  color: var(--t1);
}

/* ── 类型选择 ── */
.app-types {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.app-type {
  height: 30px;
  padding: 0 13px;
  border: 1px solid var(--hair-2);
  border-radius: 7px;
  background: var(--inp-bg);
  color: var(--t2);
  font: inherit;
  font-size: 11.5px;
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.app-type--on {
  border-color: var(--acc);
  box-shadow: 0 0 0 3px var(--acc-glow);
  color: var(--acc);
  font-weight: 600;
}

/* ── 操作行 ── */
.app-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0 8px;
}

.app-actions__picked {
  font-size: 11.5px;
  color: var(--t3);
}

/* ── 已确认品种 ── */
.app-picked {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
}

.app-picked b {
  font-size: 14px;
  font-weight: 650;
  color: var(--t1);
}

.app-picked__meta {
  font-size: 11px;
  color: var(--t4);
}

.app-unit {
  flex: none;
  font-size: 11px;
  color: var(--t4);
}

.app-code {
  max-width: 160px;
}

/* 日期控件与 AppInput 视觉对齐，走同一组 token */
.app-date {
  flex: none;
  max-width: 168px;
  font-variant-numeric: tabular-nums;
}

.app-date::-webkit-calendar-picker-indicator {
  filter: invert(var(--cal-invert, 0));
  cursor: pointer;
}

/* ── 持仓列表 ── */
.app-listempty {
  padding: 22px 0;
  text-align: center;
  font-size: 12px;
  color: var(--t4);
}

.app-rows {
  padding: 4px 0 8px;
}

/* 名称与按钮一行、字段网格独占一行，六格才有足够宽度放进完整数字 */
.app-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 13px 2px;
  border-bottom: 1px solid var(--hair);
}

.app-row:last-of-type {
  border-bottom: 0;
}

.app-row__head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.app-row__id {
  flex: 1;
  min-width: 0;
}

/* 字段网格独占整行，六列各得约 1/6 宽度 */
.app-row__grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 4px 16px;
  min-width: 0;
}

.app-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
  font-variant-numeric: tabular-nums;
}

.app-cell__k {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  white-space: nowrap;
  color: var(--t4);
}

.app-cell__v {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 650;
  line-height: 1.35;
  color: var(--t1);
}

.app-cell__sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 10.5px;
  line-height: 1.35;
  color: var(--t4);
}

/* 除息次数不换行挤成两行，宁可省略也不把单元格撑高 */
.app-cell__link {
  overflow: hidden;
  padding: 0;
  border: 0;
  background: none;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  font: inherit;
  font-size: 10.5px;
  line-height: 1.35;
  color: var(--acc);
  cursor: pointer;
}

.app-cell__link:hover {
  text-decoration: underline;
}

/* 中栏变窄时六格会挤到放不下数字，提前折成三列两行 */
@media (max-width: 1180px) {
  .app-row__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .app-row__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .app-row__head {
    flex-direction: column;
    gap: 8px;
  }
}

.app-row__name {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--t1);
}

.app-row__key {
  display: block;
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--t4);
}

.app-row__unknown {
  display: inline-block;
  margin-top: 4px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--acc-soft);
  color: var(--acc);
  font-size: 10px;
  font-weight: 650;
}

.app-row__ops {
  display: flex;
  flex: none;
  gap: 6px;
}

.app-total {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--hair-2);
  text-align: right;
  font-size: 12px;
  color: var(--t2);
}

.app-total b {
  margin-left: 6px;
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  color: var(--t1);
}

/* 批次列表：一行一批，更正与撤销都在这一行里 */
.app-batches {
  padding: 4px 0;
}

.app-batch {
  padding: 9px 0;
  border-bottom: 1px solid var(--hair);
}

.app-batch:last-child {
  border-bottom: 0;
}

.app-batch__row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11.5px;
  color: var(--t2);
}

.app-batch__shares,
.app-batch__cost,
.app-batch__money,
.app-batch__date {
  font-variant-numeric: tabular-nums;
}

.app-batch__shares {
  font-weight: 600;
  color: var(--t1);
}

.app-batch__money,
.app-batch__date {
  color: var(--t4);
}

.app-batch__ops {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.app-batch__form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
}

/* 转换弹层的下拉与输入框视觉对齐 */
.app-select {
  flex: 1;
  appearance: none;
  cursor: pointer;
}

/* ── 右栏 ── */
.app-count {
  margin-left: auto;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--s3);
  color: var(--t4);
  font-size: 10px;
  font-weight: 600;
}

.app-steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.app-step {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 11.5px;
  color: var(--t4);
}

.app-step i {
  display: grid;
  place-items: center;
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--s3);
  color: var(--t3);
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
}

.app-step--on {
  color: var(--t1);
}

.app-step--on i {
  background: var(--dn-soft);
  color: var(--dn);
}

.app-changes {
  display: flex;
  flex-direction: column;
}

.app-changes__empty {
  padding: 12px 0;
  text-align: center;
  font-size: 11px;
  color: var(--t4);
}

.app-change {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--hair);
  font-size: 11px;
}

.app-change:last-child {
  border-bottom: 0;
}

.app-change__type {
  flex: none;
  padding: 1.5px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
}

.app-change__type--buy {
  background: var(--dn-soft);
  color: var(--dn);
}

.app-change__type--sell {
  background: var(--acc-soft);
  color: var(--acc);
}

.app-change__tx {
  flex: 1;
  min-width: 0;
}

.app-change__tx b {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--t1);
}

.app-change__tx span {
  display: block;
  margin-top: 2px;
  color: var(--t3);
}

.app-change__bizdate {
  font-size: 10px;
  color: var(--t4);
}

.app-change__note {
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.app-change__time {
  flex: none;
  color: var(--t4);
}

.app-source {
  padding: 11px 13px;
  border-radius: 10px;
  background: var(--s2);
  font-size: 11.5px;
  line-height: 1.65;
  color: var(--t3);
}

.app-source b {
  display: block;
  margin-bottom: 3px;
  font-weight: 650;
  color: var(--t1);
}

/* ── 弹层 ── */
.app-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(12, 9, 6, 0.42);
  backdrop-filter: blur(3px);
}

.app-dialog {
  width: min(420px, 100%);
  padding: 22px 24px;
  border: 1px solid var(--hair);
  border-radius: 15px;
  background: var(--s1);
  box-shadow: 0 24px 64px -24px rgba(0, 0, 0, 0.4);
}

.app-dialog--wide {
  width: min(520px, 100%);
}

.app-dialog h3 {
  font-size: 15px;
  font-weight: 650;
  color: var(--t1);
}

.app-dialog__meta {
  margin: 6px 0 10px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--t3);
}

.app-dialog__ops {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.app-history {
  max-height: 320px;
  overflow-y: auto;
}
</style>
