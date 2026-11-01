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
        @select="onPaneSelect"
        @footer="onSyncCatalog"
      />

      <div class="app-stage">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                持仓 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">{{ paneTitle }}</b>
              </div>
              <div class="app-eyebrow">Holdings</div>
              <h1 class="app-h1">{{ paneHeading }}</h1>
              <p class="app-lede">
                {{
                  activePane === 'entry'
                    ? '只输入基金或 ETF 代码，名称、类型与最新净值自动带出。多义代码会列出候选并要求确认，确认后写入本地品种注册表。'
                    : activePane === 'closed'
                      ? '全部卖出的品种会离开总览，在这里仍能看到累计买卖与最后变动日，历史记录照样可以更正或删除。'
                      : '按品种聚合的持有中份额、成本与收益，买入、卖出、转换与批次更正都在这里完成；新持仓请到「录入持仓」页。'
                }}
              </p>
              <div class="app-daybar">
                <AppButton size="sm" @click="goEntry">
                  {{ activePane === 'entry' ? '查看当前持仓' : '录入新持仓' }}
                </AppButton>
                <span class="app-daybar__meta">
                  {{
                    activePane === 'entry'
                      ? '录入完成会自动跳回持仓列表'
                      : '卖出支持填写实际成交日期，补录早前的卖出不会记成今天'
                  }}
                </span>
              </div>
            </div>

            <!-- 空状态引导：尚未录入任何持仓 -->
            <div v-if="emptyState" class="app-empty">
              <div class="app-empty__ico" v-html="ICONS.compass" />
              <b>尚未录入持仓</b>
              <p>录入第一只基金或 ETF 后，总览、预估与邮件才会开始工作。输入 6 位代码开始。</p>
              <button type="button" class="app-empty__btn" @click="goEntry">开始录入</button>
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

          <!-- 已清仓：清仓后品种会从总览消失，这里把它找回来继续改 -->
          <AppGroup
            v-show="activePane === 'closed'"
            title="已清仓品种"
            code="CLOSED"
            :meta="`${closedItems.length} 只`"
          >
            <template #icon>
              <span v-html="ICONS.archive" />
            </template>

            <!-- 重复批次不修会让份额虚高、收益算错，入口放在这里，平时不占视线 -->
            <div class="app-importbar">
              <AppButton size="sm" :disabled="dupLoading" @click="onPreviewDuplicate">
                {{ dupLoading ? '检查中…' : '检查重复批次' }}
              </AppButton>
              <AppButton
                v-if="dupRows.length"
                size="sm"
                :disabled="dupCleaning"
                @click="onCleanDuplicate"
              >
                {{ dupCleaning ? '清理中…' : `清理 ${dupRows.length} 组重复` }}
              </AppButton>
              <span v-if="dupNote" class="app-importbar__note">{{ dupNote }}</span>
            </div>

            <div v-if="dupRows.length" class="app-orders">
              <div
                v-for="row in dupRows"
                :key="`${row.varietyKey}-${row.keepId}`"
                class="app-order"
              >
                <div class="app-order__main">
                  <div class="app-order__top">
                    <b class="app-order__name">{{ row.varietyName }}</b>
                    <span class="app-order__action">重复 {{ row.count }} 条</span>
                    <span class="app-order__amount">{{ fmt(row.shares) }} 份</span>
                  </div>
                  <div class="app-order__meta">
                    <span>成本价 {{ fmt(row.costPrice) }}</span>
                    <span>买入 {{ row.openDate ?? '—' }}</span>
                    <span>保留最早一条，删 {{ (row.count ?? 1) - 1 }} 条</span>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="!closedItems.length" class="app-listempty">
              还没有清仓过的品种。全部卖出后，品种会出现在这里，历史买卖仍可更正或删除。
            </div>

            <div v-else class="app-rows">
              <div v-for="item in closedItems" :key="item.varietyKey" class="app-row">
                <div class="app-row__head">
                  <div class="app-row__id">
                    <b class="app-row__name">{{ item.varietyName ?? item.varietyKey }}</b>
                    <span class="app-row__key"
                      >{{ item.varietyCode }} · {{ item.categoryLabel }}</span
                    >
                  </div>
                  <div class="app-row__ops">
                    <AppButton size="sm" @click="showChanges(item)">变动</AppButton>
                    <AppButton size="sm" @click="onResetClosed(item)">清空记录</AppButton>
                  </div>
                </div>
                <div class="app-row__grid">
                  <div class="app-cell">
                    <span class="app-cell__k">累计买入</span>
                    <b class="app-cell__v">{{ fmt(item.buySharesTotal) }} 份</b>
                  </div>
                  <div class="app-cell">
                    <span class="app-cell__k">累计卖出</span>
                    <b class="app-cell__v">{{ fmt(item.sellSharesTotal) }} 份</b>
                  </div>
                  <div class="app-cell">
                    <span class="app-cell__k">最后变动</span>
                    <b class="app-cell__v">{{ item.lastOpenDate ?? '—' }}</b>
                  </div>
                </div>
                <p v-if="item.note" class="app-cell__sub">{{ item.note }}</p>
              </div>
            </div>
          </AppGroup>
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
                  <span>{{ fmt(change.shares) }} 份 · 净值 {{ fmt(change.unitPrice) }}</span>
                  <span v-if="change.openDate" class="app-change__bizdate">
                    {{ change.changeType === 'BUY' ? '买入' : '卖出' }} {{ change.openDate }}
                  </span>
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
          补录早前的成交请改卖出日期，只会扣减建仓日不晚于该日的批次。
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
        <AppField label="卖出日期" description="默认今天，补录时填实际成交日">
          <input
            :value="sellDate"
            type="date"
            class="app-input app-date"
            @input="sellDate = ($event.target as HTMLInputElement).value"
          />
        </AppField>
        <AppField label="卖出净值" description="元/份，按支付宝订单填；留空按先进先出加权成本记账">
          <AppInput
            :model-value="sellUnitPrice"
            :numeric="true"
            inputmode="decimal"
            :placeholder="selling.latestPrice != null ? String(selling.latestPrice) : '1.5000'"
            @update:model-value="sellUnitPrice = $event"
          />
          <span class="app-unit">元/份</span>
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
          按支付宝订单补录：只填金额时，份额与成本价按当日净值自动折算。
        </p>
        <AppField label="买入日期" description="默认今天，补录时填实际成交日">
          <input
            :value="buyDate"
            type="date"
            class="app-input app-date"
            @input="buyDate = ($event.target as HTMLInputElement).value"
          />
        </AppField>
        <AppField
          label="买入金额"
          description="元，与份额二选一；填了金额按净值自动折算"
          :error="buyError"
        >
          <AppInput
            :model-value="buyAmount"
            :numeric="true"
            :invalid="Boolean(buyError)"
            inputmode="decimal"
            placeholder="10.00"
            @update:model-value="buyAmount = $event"
          />
          <span class="app-unit">元</span>
        </AppField>
        <AppField label="买入份额" description="与金额二选一；手填份额时金额只作参考">
          <AppInput
            :model-value="buyShares"
            :numeric="true"
            inputmode="decimal"
            placeholder="100"
            @update:model-value="buyShares = $event"
          />
          <span class="app-unit">份</span>
        </AppField>
        <AppField label="买入成本价" description="留空按当日净值带出">
          <AppInput
            :model-value="buyCostPrice"
            :numeric="true"
            inputmode="decimal"
            :placeholder="buying.latestPrice != null ? String(buying.latestPrice) : '1.5000'"
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
        <AppField label="转换日期" description="默认今天，补录时填实际确认日">
          <input
            :value="convertDate"
            type="date"
            class="app-input app-date"
            @input="convertDate = ($event.target as HTMLInputElement).value"
          />
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
              <b>{{ fmt(row.shares) }} 份 · 净值 {{ fmt(row.unitPrice) }}</b>
              <span v-if="row.amount != null" class="app-change__amt">
                {{ fmtMoney(row.amount) }} 元
              </span>
              <span v-if="row.openDate" class="app-change__bizdate">
                {{ row.changeType === 'BUY' ? '买入' : '卖出' }} {{ row.openDate }}
              </span>
              <span class="app-change__note">{{ row.note }}</span>
            </div>
            <span class="app-change__ops">
              <button
                v-if="row.note && !row.note.includes('更正')"
                type="button"
                class="app-cell__link"
                @click="startEditChange(row)"
              >
                更正
              </button>
              <button
                v-if="row.changeType === 'BUY' && row.note && !row.note.includes('更正')"
                type="button"
                class="app-cell__link"
                @click="onDeleteChange(row)"
              >
                删除
              </button>
              <span>{{ shortTime(row.createTime) }}</span>
            </span>
          </div>
        </div>
        <div v-if="editingSell" class="app-selledit">
          <b>
            {{ editingSell.changeType === 'BUY' ? '更正买入' : '更正卖出' }}记录
            {{ editingSell.id }}
          </b>
          <div class="app-selledit__row">
            <label>{{ editingSell.changeType === 'BUY' ? '建仓日期' : '卖出日期' }}</label>
            <input v-model="sellEditDate" type="date" class="app-input app-date" />
          </div>
          <div v-if="editingSell.changeType === 'BUY'" class="app-selledit__row">
            <label>份额</label>
            <AppInput
              :model-value="buyEditShares"
              :numeric="true"
              inputmode="decimal"
              @update:model-value="buyEditShares = $event"
            />
          </div>
          <div class="app-selledit__row">
            <label>{{ editingSell.changeType === 'BUY' ? '成本价' : '订单净值' }}</label>
            <AppInput
              :model-value="sellEditPrice"
              :numeric="true"
              inputmode="decimal"
              @update:model-value="sellEditPrice = $event"
            />
          </div>
          <div class="app-dialog__ops">
            <AppButton variant="primary" :disabled="sellEditSaving" @click="onSaveSellEdit">
              {{ sellEditSaving ? '保存中…' : '保存更正' }}
            </AppButton>
            <AppButton @click="editingSell = null">取消</AppButton>
          </div>
        </div>
        <div class="app-dialog__ops">
          <AppButton @click="historyOf = null">关闭</AppButton>
        </div>
      </div>
    </div>

    <!-- 批次更正弹层：头部与底部固定，只有批次列表滚动，避免批次一多就点不到按钮 -->
    <div v-if="batchOf" class="app-mask" @click.self="batchOf = null">
      <div class="app-dialog app-dialog--wide app-dialog--split">
        <div class="app-dialog__head">
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
          <!-- 清空入口放在弹层头部：录错太多时整只推倒重录，比逐批次撤销快 -->
          <div class="app-reset">
            <div class="app-reset__tx">
              <b>清空该基金的全部本地记录</b>
              <span>
                删除持仓批次、变动历史、预估留存、误差与黄金 β；品种本身（名称、代码、类型）保留，
                净值序列是公共数据不清。
              </span>
            </div>
            <AppButton size="sm" :disabled="resetting" @click="onResetVariety">
              {{ resetting ? '清空中…' : '清空重录' }}
            </AppButton>
          </div>
        </div>
        <div class="app-dialog__body">
          <div v-if="!batchOf.batches?.length" class="app-changes__empty">没有持有中的批次</div>
          <div class="app-dialog__body">
            <div v-if="!batchOf.batches?.length" class="app-changes__empty">没有持有中的批次</div>
            <div v-else class="app-batches">
              <div v-for="batch in batchOf.batches" :key="batch.id" class="app-batch">
                <div class="app-batch__row">
                  <span class="app-batch__shares">{{ fmt(batch.shares) }} 份</span>
                  <span class="app-batch__cost">{{ fmt(batch.costPrice) }} 元/份</span>
                  <span class="app-batch__money">成本 {{ fmtMoney(batch.costAmount) }} 元</span>
                  <span class="app-batch__date">买入 {{ batch.openDate ?? '—' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="app-dialog__foot">
          <AppButton @click="batchOf = null">关闭</AppButton>
        </div>
      </div>
    </div>

    <!-- 分红明细弹层：逐笔列出除息日与每份金额，供与平台核对；每笔可单独改现金/再投 -->
    <div v-if="dividendOf" class="app-mask" @click.self="dividendOf = null">
      <div class="app-dialog app-dialog--wide">
        <h3>{{ dividendOf.varietyName }} 的除息明细</h3>
        <p class="app-dialog__meta">{{ dividendOf.dividendNote ?? '' }}</p>
        <div class="app-history">
          <div v-for="row in dividendOf.dividendDetails ?? []" :key="row.date" class="app-change">
            <span class="app-change__type app-change__type--buy">{{ row.date }}</span>
            <div class="app-change__tx">
              <b>每份 {{ fmt(row.perShare) }} 元</b>
              <span class="app-change__note">
                {{
                  overrideOf(row.date) === 'REINVEST' ? '红利再投（折份额，不计现金）' : '现金分红'
                }}
                · 除息日持有 {{ fmt(row.shares) }} 份，计
                {{ overrideOf(row.date) === 'REINVEST' ? '0' : fmtMoney(row.amount) }} 元
              </span>
            </div>
            <span class="app-change__ops">
              <button
                type="button"
                class="app-cell__link"
                @click="toggleDividendOverride(row.date as string)"
              >
                {{ overrideOf(row.date) === 'REINVEST' ? '改为现金' : '改为再投' }}
              </button>
            </span>
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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
  closed as holdingClosed,
  convert as convertHolding,
  deleteChange as deleteChangeApi,
  overview as holdingOverview,
  sell as sellHolding,
  updateChange as updateSellChangeApi,
} from '@/api/holdingController'
import { reset as resetVarietyApi, save as saveVariety } from '@/api/varietyController'
import { clean as duplicateClean, preview as duplicatePreview } from '@/api/maintenanceController'
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
  archive:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="4.5" rx="1.2"/><path d="M5 8.5V19a1.5 1.5 0 0 0 1.5 1.5h11A1.5 1.5 0 0 0 19 19V8.5"/><path d="M10 12.5h4"/></svg>',
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

/** 侧栏锚点：默认展示当前持仓，录入是独立页面 */
const railItems = [
  { key: 'list', label: '当前持仓', icon: ICONS.list! },
  { key: 'closed', label: '已清仓', icon: ICONS.archive! },
  { key: 'entry', label: '录入持仓', icon: ICONS.search! },
]

/** 路由，用于在「当前持仓」与「录入持仓」两个地址间切换 */
const route = useRoute()
const router = useRouter()

/** 当前侧栏页，从路由初始化，/holding/entry 直接落到录入页 */
const activePane = ref(route.name === 'holding-entry' ? 'entry' : 'list')

/** 切换侧栏页并同步地址，录入与持仓是两个独立入口 */
function onPaneSelect(key: string | number): void {
  const pane = String(key)
  activePane.value = pane
  void router.push({ name: pane === 'entry' ? 'holding-entry' : 'holding' })
  // 已清仓列表独立于总览，切进去时现拉一次
  if (pane === 'closed') {
    void loadClosed()
  }
}

/** 头部按钮：在两个页面间往返 */
function goEntry(): void {
  onPaneSelect(activePane.value === 'entry' ? 'list' : 'entry')
}

// 直达 /holding 时同步回列表页，浏览器前进后退也跟随
watch(
  () => route.name,
  (name) => {
    activePane.value = name === 'holding-entry' ? 'entry' : 'list'
  },
)

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
 * 持仓总览行直接用生成的 `API.HoldingVarietyVO`。
 *
 * 历史上 `totalProfitLoss` / `dividendDetails[].shares` / `dividendOverrides` 都在本地扩展过，
 * 2026-10-31 Apifox 重新生成后全部进了生成层，扩展已删；以后再遇生成层缺字段，
 * 仍按「本地扩展 + 交付文档标注」的惯例处理，生成后记得删。
 */
type HoldingVarietyDisplay = API.HoldingVarietyVO

/**
 * 一条持仓批次
 *
 * 主键是 18 位雪花 ID，后端已改为按字符串下发，故独立声明而不复用生成的
 * `API.HoldingBatchVO`（那里 `id` 是 number，会误导类型检查）。
 */
interface HoldingBatchVO {
  /** 持仓批次主键，字符串形态的雪花 ID */
  id?: string
  /** 持有份额 */
  shares?: number
  /** 成本价 */
  costPrice?: number
  /** 成本金额 */
  costAmount?: number
  /** 建仓日期 */
  openDate?: string
  /** 录入时间 */
  createTime?: string
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
const sellDate = ref('')
const sellUnitPrice = ref('')

/** 加仓弹层状态 */
const buying = ref<HoldingVarietyDisplay | null>(null)
const buyShares = ref('')
const buyAmount = ref('')
const buyDate = ref('')
const buyCostPrice = ref('')
const buyError = ref('')

/** 转换弹层状态 */
const converting = ref<HoldingVarietyDisplay | null>(null)
const convertShares = ref('')
const convertDate = ref('')
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

/** 卖出记录更正状态，只改业务日期与订单净值 */
const editingSell = ref<API.HoldingChangeVO | null>(null)
const sellEditDate = ref('')
const sellEditPrice = ref('')
/** 买入更正时的份额，卖出更正不用这个字段 */
const buyEditShares = ref('')
const sellEditSaving = ref(false)

/** 已清仓品种列表 */
const closedItems = ref<HoldingVarietyDisplay[]>([])

/** 重复批次检查结果与进行态 */
const dupRows = ref<API.DuplicateCleanVO[]>([])
const dupLoading = ref(false)
const dupCleaning = ref(false)
const dupNote = ref('')

/** 当前页签的中文标题，面包屑与标题共用 */
const paneTitle = computed(() => {
  if (activePane.value === 'entry') {
    return '录入'
  }
  return activePane.value === 'closed' ? '已清仓' : '总览'
})

/** 当前页签的页面标题 */
const paneHeading = computed(() => {
  if (activePane.value === 'entry') {
    return '录入持仓'
  }
  return activePane.value === 'closed' ? '已清仓品种' : '当前持仓'
})

/** 批次更正弹层状态，批次单独用本地类型，因为主键是字符串形态的雪花 ID */
const batchOf = ref<
  (Omit<HoldingVarietyDisplay, 'batches'> & { batches?: HoldingBatchVO[] }) | null
>(null)

/** 清空该品种全部本地记录时的进行态，避免连点触发两次 */
const resetting = ref(false)

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

/** 今天的本地日期 yyyy-MM-dd，供日期输入框默认值 */
function today(): string {
  const now = new Date()
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
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
    onPaneSelect('list')
  } catch {
    // request 层已弹出具体原因
  } finally {
    saving.value = false
  }
}

/** 打开卖出弹层，日期默认今天 */
function openSell(item: API.HoldingVarietyVO): void {
  selling.value = item
  sellShares.value = ''
  sellDate.value = today()
  sellUnitPrice.value = ''
  sellError.value = ''
}

/** 打开加仓弹层，日期默认今天、成本价默认当前净值便于快速加仓 */
function openBuy(item: HoldingVarietyDisplay): void {
  buying.value = item
  buyShares.value = ''
  buyAmount.value = ''
  buyDate.value = today()
  buyCostPrice.value = item.latestPrice != null ? String(item.latestPrice) : ''
  buyError.value = ''
}

/** 提交加仓：金额与份额至少一项，成本价留空由后端按当日净值带出 */
async function onBuy(): Promise<void> {
  if (!buying.value?.varietyKey) {
    return
  }
  const sharesValue = buyShares.value.trim() === '' ? null : Number(buyShares.value)
  const amountValue = buyAmount.value.trim() === '' ? null : Number(buyAmount.value)
  if (sharesValue !== null && (!Number.isFinite(sharesValue) || sharesValue <= 0)) {
    buyError.value = '买入份额必须为正数'
    return
  }
  if (amountValue !== null && (!Number.isFinite(amountValue) || amountValue <= 0)) {
    buyError.value = '买入金额必须为正数'
    return
  }
  if (sharesValue === null && amountValue === null) {
    buyError.value = '买入金额与买入份额至少填一个'
    return
  }
  const costValue = buyCostPrice.value.trim() === '' ? null : Number(buyCostPrice.value)
  buyError.value = ''
  try {
    await buyHolding({
      varietyKey: buying.value.varietyKey,
      ...(sharesValue !== null ? { shares: sharesValue } : {}),
      ...(amountValue !== null ? { amount: amountValue } : {}),
      ...(costValue !== null && Number.isFinite(costValue) ? { costPrice: costValue } : {}),
      ...(buyDate.value ? { openDate: buyDate.value } : {}),
    } as API.HoldingBuyRequest)
    ElMessage.success(`已加仓 ${buying.value.varietyName}`)
    buying.value = null
    await loadAll()
  } catch {
    // request 层已弹出具体原因
  }
}

/** 打开转换弹层，日期默认今天 */
function openConvert(item: HoldingVarietyDisplay): void {
  converting.value = item
  convertShares.value = ''
  convertDate.value = today()
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
      ...(convertDate.value ? { openDate: convertDate.value } : {}),
    })
    ElMessage.success(result.data?.note ?? '转换已完成')
    converting.value = null
    await loadAll()
  } catch {
    // request 层已弹出具体原因
  }
}

/** 提交卖出，日期默认今天；填了净值按订单记账，留空按先进先出加权成本 */
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
  const priceValue = sellUnitPrice.value.trim() === '' ? null : Number(sellUnitPrice.value)
  try {
    await sellHolding({
      varietyKey: selling.value.varietyKey,
      shares: value,
      ...(sellDate.value ? { tradeDate: sellDate.value } : {}),
      ...(priceValue !== null && Number.isFinite(priceValue) && priceValue > 0
        ? { unitPrice: priceValue }
        : {}),
    } as API.HoldingSellRequest)
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
  editingSell.value = null
  try {
    const result = await holdingChanges({ varietyKey: item.varietyKey })
    historyRows.value = result.data ?? []
  } catch {
    historyRows.value = []
  }
}

/** 进入更正态，买入与卖出共用一套表单，字段按类型显隐 */
function startEditChange(row: API.HoldingChangeVO): void {
  editingSell.value = row
  sellEditDate.value = row.openDate ?? ''
  sellEditPrice.value = row.unitPrice != null ? String(row.unitPrice) : ''
  buyEditShares.value = row.shares != null ? String(row.shares) : ''
}

/** 保存更正：买入可改份额、成本价与日期，卖出只改日期与净值 */
async function onSaveSellEdit(): Promise<void> {
  const row = editingSell.value
  if (row?.id == null) {
    return
  }
  const isBuy = row.changeType === 'BUY'
  if (
    !sellEditDate.value &&
    sellEditPrice.value.trim() === '' &&
    !(isBuy && buyEditShares.value.trim())
  ) {
    ElMessage.info('日期、份额与净值至少改一个')
    return
  }
  const priceValue = sellEditPrice.value.trim() === '' ? null : Number(sellEditPrice.value)
  if (priceValue !== null && (!Number.isFinite(priceValue) || priceValue <= 0)) {
    ElMessage.error(isBuy ? '成本价必须为正数' : '订单净值必须为正数')
    return
  }
  const sharesValue = buyEditShares.value.trim() === '' ? null : Number(buyEditShares.value)
  if (isBuy && sharesValue !== null && (!Number.isFinite(sharesValue) || sharesValue <= 0)) {
    ElMessage.error('份额必须为正数')
    return
  }
  sellEditSaving.value = true
  try {
    await updateSellChangeApi(
      {
        id: row.id,
        ...(sellEditDate.value ? { tradeDate: sellEditDate.value } : {}),
        ...(priceValue !== null ? { unitPrice: priceValue } : {}),
        ...(isBuy && sharesValue !== null ? { shares: sharesValue } : {}),
      },
      { silent: true },
    )
    ElMessage.success('已更正，变动历史里追加了一条说明')
    editingSell.value = null
    if (historyOf.value) {
      await showChanges(historyOf.value)
    }
    await loadAll()
    if (activePane.value === 'closed') {
      await loadClosed()
    }
  } catch {
    // request 层已弹出具体原因
  } finally {
    sellEditSaving.value = false
  }
}

/** 删除一条买入变动，卖出不支持直接删，后端会拒绝并说明原因 */
async function onDeleteChange(row: API.HoldingChangeVO): Promise<void> {
  if (row.id == null) {
    return
  }
  if (!window.confirm(`确认删除这条买入记录？对应的持仓批次也会一并删除。`)) {
    return
  }
  try {
    await deleteChangeApi({ id: row.id } as unknown as API.HoldingChangeRevokeRequest, {
      silent: true,
    })
    ElMessage.success('已删除')
    if (historyOf.value) {
      await showChanges(historyOf.value)
    }
    await loadAll()
    if (activePane.value === 'closed') {
      await loadClosed()
    }
  } catch {
    // request 层已弹出具体原因
  }
}

/** 检查重复批次，只读不写 */
async function onPreviewDuplicate(): Promise<void> {
  dupLoading.value = true
  dupNote.value = ''
  try {
    const result = await duplicatePreview()
    dupRows.value = result.data ?? []
    dupNote.value = dupRows.value.length
      ? `发现 ${dupRows.value.length} 组完全重复的批次，清理后每只基金只保留最早一条`
      : '没有发现完全重复的批次'
  } catch {
    dupRows.value = []
  } finally {
    dupLoading.value = false
  }
}

/** 执行清理，二次确认后才写库 */
async function onCleanDuplicate(): Promise<void> {
  if (!dupRows.value.length) {
    return
  }
  if (!window.confirm(`确认清理 ${dupRows.value.length} 组重复？每组只保留最早一条，其余删除。`)) {
    return
  }
  dupCleaning.value = true
  try {
    const result = await duplicateClean()
    dupNote.value = result.data ?? '已清理'
    dupRows.value = []
    ElMessage.success(dupNote.value)
    await loadAll()
    await loadClosed()
  } catch {
    // request 层已弹出具体原因
  } finally {
    dupCleaning.value = false
  }
}

/** 拉取已清仓品种 */
async function loadClosed(): Promise<void> {
  try {
    const result = await holdingClosed()
    closedItems.value = (result.data ?? []) as HoldingVarietyDisplay[]
  } catch {
    closedItems.value = []
  }
}

/** 清空某只已清仓品种的全部记录，与「改批次」里的清空重录同义 */
async function onResetClosed(item: HoldingVarietyDisplay): Promise<void> {
  const key = item.varietyKey
  if (!key) {
    return
  }
  const label = item.varietyName ?? key
  if (!window.confirm(`确认清空「${label}」的全部记录？此操作不可撤销。`)) {
    return
  }
  try {
    await resetVarietyApi({ varietyKey: key } as unknown as API.resetParams)
    ElMessage.success('已清空')
    await loadClosed()
    await loadAll()
  } catch {
    // request 层已弹出具体原因
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
  onPaneSelect('entry')
  void nextTick(() => {
    const element = document.querySelector<HTMLInputElement>('input.app-code')
    element?.focus()
  })
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
    // 生成的 typings 里批次 id 是 number，而后端已改为字符串下发，这里的转换只是对齐类型
    overview.value = (overviewResult.value.data ?? {}) as HoldingOverviewDisplay
  }
  if (changesResult.status === 'fulfilled') {
    changes.value = (changesResult.value.data ?? []).slice(0, 8)
  }
}

/** 打开批次更正弹层 */
/** 打开批次更正弹层 */
function openBatches(item: HoldingVarietyDisplay): void {
  // 生成的 typings 把批次 id 标为 number，后端实际下发字符串，这里按本地类型对齐
  batchOf.value = item as Omit<HoldingVarietyDisplay, 'batches'> & { batches?: HoldingBatchVO[] }
}

/** 清空该品种的全部本地记录，品种注册与净值序列保留；二次确认后才发请求 */
async function onResetVariety(): Promise<void> {
  const item = batchOf.value
  const key = item?.varietyKey
  if (!key || resetting.value) {
    return
  }
  const label = item?.varietyName ?? key
  if (!window.confirm(`确认清空「${label}」的全部持仓、变动与预估留存？此操作不可撤销。`)) {
    return
  }
  resetting.value = true
  try {
    // 生成层里 resetParams 有两份（参数重置与品种重置同名），TS 认成了 group 那份，这里按实际契约断言
    const result = await resetVarietyApi({ varietyKey: key } as unknown as API.resetParams)
    ElMessage.success(result.data ? String(result.data) : '已清空，可以重新录入')
    batchOf.value = null
    await loadAll()
  } catch {
    // request 层已弹出具体原因
  } finally {
    resetting.value = false
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

/** 取某次除息生效的分红方式：覆盖优先，未覆盖跟随品种默认 */
function overrideOf(date?: string): string {
  if (!date) {
    return 'CASH'
  }
  const overrides = parseOverrides(dividendOf.value?.dividendOverrides)
  if (overrides[date]) {
    return overrides[date]!
  }
  return dividendOf.value?.dividendMethod === 'REINVEST' ? 'REINVEST' : 'CASH'
}

/** 解品种上的覆盖 JSON，坏串按空表处理 */
function parseOverrides(raw?: string | null): Record<string, 'CASH' | 'REINVEST'> {
  if (!raw) {
    return {}
  }
  try {
    return JSON.parse(raw) as Record<string, 'CASH' | 'REINVEST'>
  } catch {
    return {}
  }
}

/** 切换某次除息的分红方式，写入品种覆盖表后整页刷新 */
async function toggleDividendOverride(date: string): Promise<void> {
  const item = dividendOf.value
  if (!item?.varietyKey) {
    return
  }
  const overrides = parseOverrides(item.dividendOverrides)
  const next = overrideOf(date) === 'REINVEST' ? 'CASH' : 'REINVEST'
  overrides[date] = next
  try {
    // 品种保存是按业务主键覆盖，身份字段一并回写防止被清空
    await saveVariety({
      varietyKey: item.varietyKey,
      dividendOverrides: JSON.stringify(overrides),
      ...(item.varietyCode ? { varietyCode: item.varietyCode } : {}),
      ...(item.varietyType ? { varietyType: item.varietyType } : {}),
      ...(item.varietyName ? { varietyName: item.varietyName } : {}),
      ...(item.category ? { category: item.category } : {}),
      ...(item.confirmed != null ? { confirmed: item.confirmed } : {}),
    } as unknown as API.VarietySaveRequest)
    ElMessage.success(`${date} 已改为${next === 'REINVEST' ? '红利再投' : '现金分红'}`)
    dividendOf.value = { ...item, dividendOverrides: JSON.stringify(overrides) }
    await loadAll()
  } catch {
    // request 层已弹出具体原因
  }
}

onMounted(() => {
  void loadAll()
  // 已清仓数量在侧栏不显示，但进来就拉一次，切页签时不用等
  void loadClosed()
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
/* 重复批次清单：与核对页的行样式同构，这里只需最简的一套 */
.app-importbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
}

.app-importbar__note {
  font-size: 11px;
  color: var(--t4);
}

.app-orders {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 10px;
}

.app-order {
  display: flex;
  gap: 8px;
  padding: 9px 11px;
  border: 1px solid var(--hair);
  border-radius: 9px;
  background: var(--s1);
}

.app-order__main {
  flex: 1;
  min-width: 0;
}

.app-order__top {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  font-size: 12.5px;
}

.app-order__name {
  font-weight: 650;
  color: var(--t1);
}

.app-order__action {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--acc-soft);
  color: var(--acc);
  font-size: 10.5px;
}

.app-order__amount {
  font-variant-numeric: tabular-nums;
  color: var(--t2);
}

.app-order__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 3px;
  font-size: 11px;
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

.app-change__ops {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
  color: var(--t4);
}

.app-selledit {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid var(--hair-2);
  border-radius: 10px;
  background: var(--s2);
  font-size: 12.5px;
}

.app-selledit__row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-selledit__row label {
  flex: none;
  width: 64px;
  color: var(--t3);
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
  display: flex;
  align-items: flex-start;
  justify-content: center;
  /* 内容比视口高时整层可滚，且不被居中裁掉顶部 */
  overflow-y: auto;
  padding: 24px;
  background: rgba(12, 9, 6, 0.42);
  backdrop-filter: blur(3px);
}

.app-dialog {
  width: min(420px, 100%);
  /* 内容超长时弹层自身滚动，不能把底部按钮挤出视口 */
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  padding: 22px 24px;
  border: 1px solid var(--hair);
  border-radius: 15px;
  background: var(--s1);
  box-shadow: 0 24px 64px -24px rgba(0, 0, 0, 0.4);
}

.app-dialog--wide {
  width: min(520px, 100%);
}

/* 头部与底部固定、中间列表滚动，长列表下底部按钮始终可见 */
.app-dialog--split {
  display: flex;
  flex-direction: column;
  /* 覆盖基础样式的整层滚动，改由内部列表滚动 */
  overflow: hidden;
  padding: 0;
}

.app-dialog--split .app-dialog__head {
  flex: none;
  padding: 22px 24px 12px;
  border-bottom: 1px solid var(--hair);
}

.app-dialog--split .app-dialog__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 24px;
}

.app-dialog--split .app-dialog__foot {
  display: flex;
  flex: none;
  gap: 8px;
  padding: 12px 24px 18px;
  border-top: 1px solid var(--hair);
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

/* 清空入口：靠下并用描边圈出，避免和上面的常规操作混在一起 */
.app-reset {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding: 10px 12px;
  border: 1px dashed var(--hair-2);
  border-radius: 10px;
  background: var(--s2);
}

.app-reset__tx {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--t3);
}

.app-reset__tx b {
  display: block;
  margin-bottom: 2px;
  font-size: 11.5px;
  font-weight: 650;
  color: var(--t2);
}

.app-history {
  max-height: 320px;
  overflow-y: auto;
}
</style>
