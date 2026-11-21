<template>
  <div class="app-page">
    <div class="app-atmos">
      <div class="app-atmos__a" />
      <div class="app-atmos__b" />
    </div>

    <AppTopBar active="strategy">
      <template #status>
        {{ mail.ready ? '邮件通道就绪' : '邮件通道未就绪' }}
      </template>
    </AppTopBar>

    <AppPulseBar
      :estimable-count="usableCount"
      :total-count="railTotal"
      :market-mood="mail.ready ? '通道就绪' : '待配置'"
      :next-note="lastSavedAt ? `最后保存 ${lastSavedAt}` : ''"
    />

    <div class="app-body">
      <AppSideRail
        section-label="策略分组"
        :items="railItems"
        :active="activeGroup"
        footer-label="全部恢复默认"
        :footer-icon="GROUP_ICONS.ALERT"
        @select="scrollTo"
        @footer="resetOne('INDICATOR')"
      />

      <div class="app-stage">
        <main class="app-center">
          <div class="app-center__in">
            <div class="app-head">
              <div class="app-crumb">
                设置 <span class="app-crumb__sl">/</span>
                <b class="app-crumb__b">参数</b>
              </div>
              <div class="app-eyebrow">Settings</div>
              <h1 class="app-h1">参数设置</h1>
              <p class="app-lede">
                红利与网格的参数<b>按标的分开存</b>：先选一只持仓里的基金，看清它跟踪的指数与当前股息率，
                再给这只设它自己的买卖阈值与金额。指标与债券是全局参数，所有标的共用。
              </p>
            </div>

            <div v-if="loading" class="app-loading">正在读取参数…</div>

            <div v-else-if="globalError" class="app-alert">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
              >
                <circle cx="12" cy="12" r="9.2" />
                <path d="M12 7.5v5.2M12 16.3v.2" />
              </svg>
              <span>{{ globalError }}</span>
            </div>

            <!-- ══════════ 红利：先选基金，再给这只设参数 ══════════ -->
            <AppGroup
              v-if="activeGroup === 'DIVIDEND'"
              title="红利监控对象"
              code="WATCH"
              :meta="dividendWatch.length ? `${dividendWatch.length} 只持仓` : '当前无红利持仓'"
              :meta-ok="!dividendBlocked.length"
            >
              <template #icon>
                <span v-html="GROUP_ICONS.DIVIDEND" />
              </template>

              <div v-if="!dividendWatch.length" class="app-listempty">
                当前没有持有中的红利基金。请在持仓页把基金类型确认为「红利」并登记跟踪指数，
                这里才会列出它参与加减仓建议。
              </div>

              <div v-else class="app-pick">
                <button
                  v-for="row in dividendWatch"
                  :key="row.varietyKey"
                  type="button"
                  class="app-pick__item"
                  :class="{ 'app-pick__item--on': selectedDividend === row.varietyKey }"
                  @click="selectDividend(row)"
                >
                  <span class="app-pick__nm">{{ row.varietyName ?? row.varietyKey }}</span>
                  <span class="app-pick__cd">{{ row.varietyCode }}</span>
                  <span class="app-pick__tag" :class="dividendTagClass(row)">
                    {{ dividendTagText(row) }}
                  </span>
                </button>
              </div>

              <!-- 选中这只之后的详情：跟踪指数 → 股息率 → 它自己的参数 -->
              <template v-if="dividendDetail">
                <div class="app-spec">
                  <div class="app-spec__row">
                    <span class="app-spec__k">跟踪指数</span>
                    <span class="app-spec__v">
                      {{ dividendDetail.indexCode ?? '未登记' }}
                      <template v-if="dividendDetail.indexName">
                        · {{ dividendDetail.indexName }}
                      </template>
                    </span>
                  </div>
                  <div class="app-spec__row">
                    <span class="app-spec__k">当前股息率</span>
                    <span class="app-spec__v app-spec__v--num">
                      <template v-if="dividendDetail.dividendYield != null">
                        {{ fmt(dividendDetail.dividendYield) }}%
                        <span class="app-spec__sub">
                          数据 {{ dividendDetail.dataDate ?? '—' }}
                          <template v-if="dividendDetail.ageDays != null">
                            · 距今 {{ dividendDetail.ageDays }} 天
                          </template>
                        </span>
                        <span
                          v-if="dividendDetail.stale"
                          class="app-pill app-pill--warn app-spec__pill"
                        >
                          已过期
                        </span>
                      </template>
                      <template v-else>
                        <span class="app-spec__missing">尚无股息率数据</span>
                      </template>
                    </span>
                  </div>
                  <div class="app-spec__row">
                    <span class="app-spec__k">当前持有</span>
                    <span class="app-spec__v app-spec__v--num">
                      <template v-if="dividendDetail.currentAmount != null">
                        {{ fmtMoney(dividendDetail.currentAmount) }} 元
                      </template>
                      <template v-else>取不到</template>
                      <span v-if="dividendDetail.amountSource" class="app-spec__sub">
                        {{ dividendDetail.amountSource }}
                      </span>
                    </span>
                  </div>
                  <div class="app-spec__row">
                    <span class="app-spec__k">按当前参数</span>
                    <span class="app-spec__v">
                      {{ dividendDetail.actionLabel ?? '—' }}
                      <template v-if="dividendDetail.suggestAmount != null">
                        · {{ fmtMoney(dividendDetail.suggestAmount) }} 元
                      </template>
                    </span>
                  </div>
                  <ul v-if="dividendDetail.reasons?.length" class="app-spec__why">
                    <li v-for="(reason, index) in dividendDetail.reasons" :key="index">
                      {{ reason }}
                    </li>
                  </ul>
                </div>

                <!-- 股息率缺失时的两条补数入口 -->
                <div v-if="dividendDetail.dividendYield == null" class="app-fix">
                  <div class="app-fix__bar">
                    <b>这只基金缺股息率</b>
                    <span class="app-fix__note">
                      {{ fixHint }}
                    </span>
                  </div>
                  <div class="app-fix__acts">
                    <AppButton :disabled="refreshing" @click="refreshDividendOf(dividendDetail)">
                      {{ refreshing ? '刷新中…' : '立即刷新股息率' }}
                    </AppButton>
                    <AppButton @click="openManual(dividendDetail)">
                      {{ manualOpen ? '收起手工录入' : '手工录入' }}
                    </AppButton>
                    <span v-if="manualBlocked" class="app-fix__blocked">{{ manualBlocked }}</span>
                  </div>

                  <div v-if="manualOpen" class="app-manual">
                    <label class="app-manual__f">
                      <span>数据日期</span>
                      <input v-model="manualDraft.dataDate" type="date" class="app-manual__in" />
                    </label>
                    <label class="app-manual__f">
                      <span>股息率 %</span>
                      <input
                        v-model="manualDraft.dividendYield"
                        inputmode="decimal"
                        class="app-manual__in app-manual__in--num"
                        placeholder="如 4.94"
                      />
                    </label>
                    <label class="app-manual__f">
                      <span>指数全称</span>
                      <input
                        v-model="manualDraft.indexName"
                        class="app-manual__in"
                        placeholder="可留空"
                      />
                    </label>
                    <AppButton variant="primary" :disabled="manualSaving" @click="submitManual">
                      {{ manualSaving ? '提交中…' : '写入' }}
                    </AppButton>
                  </div>
                  <p class="app-hint">
                    数据日期必须填真实日期，不允许默认今天：一份三周前抄来的数字若标成今天，
                    加减仓就会依据过期数据下单。
                  </p>
                </div>

                <!-- 这只基金自己的股息率阶梯：每档写「这个股息率下该持有多少钱」 -->
                <div class="app-ladder">
                  <div class="app-ladder__head">
                    <div>
                      <b>股息率阶梯</b>
                      <span class="app-ladder__tip">
                        每档写「这个股息率下我该持有多少钱」。股息率越高越便宜，持有越多；
                        最低一档就是永远不卖的保底底仓。
                      </span>
                    </div>
                    <div class="app-ladder__acts">
                      <AppButton :disabled="dividendDraft.ladder.length >= 12" @click="addRung">
                        {{ dividendDraft.ladder.length ? '加一档' : '开始填第一档' }}
                      </AppButton>
                      <AppButton
                        v-if="dividendDraft.ladder.length"
                        :disabled="dividendDraft.ladder.length <= 2"
                        @click="removeRung"
                      >
                        删最低一档
                      </AppButton>
                    </div>
                  </div>

                  <!-- 未配置时不预填任何数字，避免把系统的猜测当成使用者的决定 -->
                  <div v-if="!dividendDraft.ladder.length" class="app-ladder__empty">
                    这只基金还没有股息率阶梯。
                    <span class="app-ladder__empty-tip">
                      系统不会替你猜阈值——当前股息率
                      {{
                        dividendDetail.dividendYield != null
                          ? fmt(dividendDetail.dividendYield) + '%'
                          : '未知'
                      }}
                      只能说明「今天贵不贵」，判断不了「该在多少买」。点「开始填第一档」自己定。
                    </span>
                  </div>

                  <div v-else class="app-ladder__grid">
                    <div
                      v-for="(rung, index) in dividendDraft.ladder"
                      :key="index"
                      class="app-ladder__row"
                    >
                      <span class="app-ladder__no">
                        {{
                          index === 0
                            ? '最高'
                            : index === dividendDraft.ladder.length - 1
                              ? '保底'
                              : index + 1
                        }}
                      </span>
                      <label class="app-ladder__f">
                        <span>股息率</span>
                        <input
                          v-model="rung.yield"
                          inputmode="decimal"
                          class="app-ladder__in"
                          placeholder="5.50"
                        />
                        <span class="app-ladder__u">%</span>
                      </label>
                      <span class="app-ladder__arw">→</span>
                      <label class="app-ladder__f">
                        <span>应持有</span>
                        <input
                          v-model="rung.amount"
                          inputmode="decimal"
                          class="app-ladder__in app-ladder__in--wide"
                          placeholder="80000"
                        />
                        <span class="app-ladder__u">元</span>
                      </label>
                      <button
                        v-if="dividendDraft.ladder.length > 2"
                        type="button"
                        class="app-ladder__del"
                        @click="removeRungAt(index)"
                      >
                        删除
                      </button>
                    </div>
                  </div>

                  <div v-if="ladderPreview.length" class="app-ladder__preview">
                    <span class="app-ladder__pv">
                      仓位上限 {{ fmtMoney(ladderPreview[0]?.amount) }} 元 · 保底
                      {{ fmtMoney(ladderPreview[ladderPreview.length - 1]?.amount) }} 元
                    </span>
                    <span class="app-ladder__pv">
                      当前股息率
                      {{
                        dividendDetail.dividendYield != null
                          ? fmt(dividendDetail.dividendYield) + '%'
                          : '未知'
                      }}
                      <template v-if="hitPreview">
                        → 应持有 {{ fmtMoney(hitPreview.amount) }} 元
                      </template>
                    </span>
                  </div>

                  <ul v-if="ladderErrors.length" class="app-ladder__err">
                    <li v-for="(issue, index) in ladderErrors" :key="index">{{ issue }}</li>
                  </ul>
                </div>

                <div class="app-actions">
                  <AppButton variant="primary" :disabled="dividendSaving" @click="saveDividendRow">
                    {{ dividendSaving ? '保存中…' : '保存这只的阶梯' }}
                  </AppButton>
                  <AppButton
                    :disabled="!dividendDetail.paramUsable && !hasDividendParam"
                    @click="clearDividendRow"
                  >
                    清除这只的配置
                  </AppButton>
                  <span v-if="dividendSavedNote" class="app-actions__picked">
                    {{ dividendSavedNote }}
                  </span>
                </div>

                <p class="app-hint">
                  同一指数的不同基金（如 A/C
                  类，或跟踪同一指数但管理不同的两只）各自持有各自的一套阶梯：
                  改这只不影响那只，金额也不是合计口径。
                </p>
              </template>
            </AppGroup>

            <!-- ══════════ 网格：先选标的，再给这只划格子 ══════════ -->
            <AppGroup
              v-if="activeGroup === 'GRID'"
              title="网格监控对象"
              code="WATCH"
              :meta="gridWatch.length ? `${gridWatch.length} 只持仓` : '当前无持仓标的'"
              :meta-ok="!gridBlocked.length"
            >
              <template #icon>
                <span v-html="GROUP_ICONS.GRID" />
              </template>

              <div v-if="!gridWatch.length" class="app-listempty">
                当前没有持有中的标的。网格按标的划格子，先把标的录入持仓，这里才会列出它。
              </div>

              <div v-else class="app-pick">
                <button
                  v-for="row in gridWatch"
                  :key="row.varietyKey"
                  type="button"
                  class="app-pick__item"
                  :class="{ 'app-pick__item--on': selectedGrid === row.varietyKey }"
                  @click="selectGrid(row)"
                >
                  <span class="app-pick__nm">{{ row.varietyName ?? row.varietyKey }}</span>
                  <span class="app-pick__cd">{{ row.varietyCode }}</span>
                  <span class="app-pick__tag" :class="gridTagClass(row)">
                    {{ gridTagText(row) }}
                  </span>
                </button>
              </div>

              <template v-if="gridDetail">
                <div class="app-spec">
                  <div class="app-spec__row">
                    <span class="app-spec__k">价格口径</span>
                    <span class="app-spec__v">
                      {{ gridDetail.basisLabel ?? '—' }}
                      <span v-if="gridDetail.proxySymbol" class="app-spec__sub">
                        对标 {{ gridDetail.proxySymbol }}
                      </span>
                    </span>
                  </div>
                  <div class="app-spec__row">
                    <span class="app-spec__k">当前价</span>
                    <span class="app-spec__v app-spec__v--num">
                      <template v-if="gridDetail.currentPrice != null">
                        {{ fmt(gridDetail.currentPrice) }} 元
                        <span class="app-spec__sub">
                          {{ gridDetail.priceDate ?? '' }} · {{ gridDetail.priceSource ?? '' }}
                        </span>
                      </template>
                      <template v-else>取不到</template>
                    </span>
                  </div>
                  <div v-if="gridDetail.positionNote" class="app-spec__row">
                    <span class="app-spec__k">区间位置</span>
                    <span
                      class="app-spec__v"
                      :class="{ 'app-spec__v--warn': gridDetail.position !== 'INSIDE' }"
                    >
                      {{ gridDetail.positionNote }}
                    </span>
                  </div>
                  <div v-if="gridDetail.customized === false" class="app-spec__row">
                    <span class="app-spec__k">配置状态</span>
                    <span class="app-spec__v app-spec__v--warn">
                      还没划格子，网格对这只不生效
                    </span>
                  </div>
                  <ul v-if="gridDetail.paramIssues?.length" class="app-spec__why">
                    <li v-for="(issue, index) in gridDetail.paramIssues" :key="index">
                      {{ issue }}
                    </li>
                  </ul>
                </div>

                <div class="app-dividend-form">
                  <AppField
                    label="价格下界"
                    description="格子区间的下沿，价格跌破后没有可买的线"
                    :error="gridError('lowerBound')"
                  >
                    <AppInput
                      :model-value="gridDraft.lowerBound"
                      :numeric="true"
                      :invalid="Boolean(gridError('lowerBound'))"
                      inputmode="decimal"
                      placeholder="1.57"
                      @update:model-value="gridDraft.lowerBound = $event"
                    />
                    <span class="app-unit">元</span>
                  </AppField>
                  <AppField
                    label="价格上界"
                    description="格子区间的上沿，必须大于下界"
                    :error="gridError('upperBound')"
                  >
                    <AppInput
                      :model-value="gridDraft.upperBound"
                      :numeric="true"
                      :invalid="Boolean(gridError('upperBound'))"
                      inputmode="decimal"
                      placeholder="1.66"
                      @update:model-value="gridDraft.upperBound = $event"
                    />
                    <span class="app-unit">元</span>
                  </AppField>
                  <AppField
                    label="格子大小"
                    description="相邻网格线的价格间距，按上下界至少要能划出两格"
                    :error="gridError('step')"
                  >
                    <AppInput
                      :model-value="gridDraft.step"
                      :numeric="true"
                      :invalid="Boolean(gridError('step'))"
                      inputmode="decimal"
                      placeholder="0.02"
                      @update:model-value="gridDraft.step = $event"
                    />
                    <span class="app-unit">元</span>
                  </AppField>
                  <AppField
                    label="每格金额"
                    description="价格穿越一条网格线时建议买卖的金额"
                    :error="gridError('amountPerGrid')"
                  >
                    <AppInput
                      :model-value="gridDraft.amountPerGrid"
                      :numeric="true"
                      :invalid="Boolean(gridError('amountPerGrid'))"
                      inputmode="decimal"
                      placeholder="40"
                      @update:model-value="gridDraft.amountPerGrid = $event"
                    />
                    <span class="app-unit">元</span>
                  </AppField>
                </div>

                <div class="app-actions">
                  <AppButton variant="primary" :disabled="gridSaving" @click="saveGridRow">
                    {{ gridSaving ? '保存中…' : '保存这只的格子' }}
                  </AppButton>
                  <AppButton :disabled="gridDetail.customized === false" @click="clearGridRow">
                    清除这只的格子
                  </AppButton>
                  <span v-if="gridSavedNote" class="app-actions__picked">{{ gridSavedNote }}</span>
                  <span v-if="gridLines.length" class="app-actions__lines">
                    划出 {{ gridLines.length }} 条线：{{ gridLines.map(fmt).join(' / ') }}
                  </span>
                </div>

                <p class="app-hint">
                  网格参数按标的各存一套，没有全局兜底：没划格子的标的在收盘前邮件里只列一行说明，
                  不会拿别的标的的区间硬套。场外基金按跟踪 ETF 的涨跌在尾盘估净值，盘中价还会变。
                </p>
              </template>
            </AppGroup>

            <!-- ══════════ 债券：全局参数 + 说明 ══════════ -->
            <template v-if="activeGroup === 'BOND'">
              <AppGroup
                v-if="activeGroupMeta"
                title="债券监控"
                code="BOND"
                :meta="
                  bondWatch.length ? `${bondWatch.length} 只债券持仓共用这组参数` : '当前无债券持仓'
                "
                :meta-ok="true"
              >
                <template #icon>
                  <span v-html="GROUP_ICONS.BOND" />
                </template>

                <AppField
                  v-for="item in activeGroupMeta.items ?? []"
                  :key="item.key"
                  :label="item.label ?? ''"
                  :description="item.description"
                  :error="errorOf('BOND', item.key ?? '')"
                >
                  <AppInput
                    :model-value="valueOf('BOND', item.key ?? '')"
                    :numeric="true"
                    :invalid="Boolean(errorOf('BOND', item.key ?? ''))"
                    inputmode="decimal"
                    @update:model-value="(value) => setValue('BOND', item.key ?? '', value)"
                  />
                  <span v-if="item.unit" class="app-unit">{{ item.unit }}</span>
                </AppField>

                <p class="app-hint">
                  债券基金不做当日预估：债券日波动常在 0.02% 上下，小于预估本身的误差量级。
                  这里改为看<b>历史净值序列</b>——净值连续多日几乎不动（横盘），或回看窗口内
                  年化斜率转负且波动放大（趋势转弱）时，收盘前邮件会提示「考虑换仓」。
                  这组阈值是全局的，所有债券持仓共用。
                </p>
              </AppGroup>
            </template>

            <!-- ══════════ 指标与警示：全局参数 ══════════ -->
            <AppGroup
              v-if="activeGroup === 'INDICATOR' || activeGroup === 'ALERT'"
              :key="activeGroupMeta?.group"
              :title="activeGroupMeta?.label ?? ''"
              :code="activeGroupMeta?.group ?? ''"
              :meta="activeGroup === 'INDICATOR' ? '已内置默认值，不改也能跑' : ''"
              :meta-ok="activeGroup === 'INDICATOR'"
            >
              <template #icon>
                <span v-html="GROUP_ICONS[activeGroupMeta?.group ?? ''] ?? ''" />
              </template>

              <AppField
                v-for="item in activeGroupMeta?.items ?? []"
                :key="item.key"
                :label="item.label ?? ''"
                :description="item.description"
                :error="errorOf(activeGroupMeta?.group ?? '', item.key ?? '')"
              >
                <AppSwitch
                  v-if="item.type === 'BOOLEAN'"
                  :model-value="valueOf(activeGroupMeta?.group ?? '', item.key ?? '') === 'true'"
                  @update:model-value="
                    (value) =>
                      setValue(
                        activeGroupMeta!.group ?? '',
                        item.key ?? '',
                        value ? 'true' : 'false',
                      )
                  "
                />
                <template v-else>
                  <AppInput
                    :model-value="valueOf(activeGroupMeta?.group ?? '', item.key ?? '')"
                    :numeric="item.type !== 'TEXT'"
                    :invalid="Boolean(errorOf(activeGroupMeta?.group ?? '', item.key ?? ''))"
                    :inputmode="item.type === 'TEXT' ? 'text' : 'decimal'"
                    @update:model-value="
                      (value) => setValue(activeGroupMeta!.group ?? '', item.key ?? '', value)
                    "
                  />
                  <span v-if="item.unit" class="app-unit">{{ item.unit }}</span>
                </template>
              </AppField>
            </AppGroup>

            <AppGroup
              v-if="activeGroup === 'MAIL'"
              title="邮件与 SMTP"
              code="MAIL"
              meta="这里保存的配置会立即用于发信，覆盖本地的同名配置"
            >
              <template #icon>
                <span v-html="GROUP_ICONS.MAIL" />
              </template>

              <AppField
                label="收件地址"
                description="多个地址用逗号分隔"
                :error="errorOf('MAIL', 'recipients')"
              >
                <AppInput
                  :model-value="draft['MAIL.recipients'] ?? ''"
                  :numeric="false"
                  :invalid="Boolean(errorOf('MAIL', 'recipients'))"
                  inputmode="email"
                  @update:model-value="(value) => (draft['MAIL.recipients'] = value)"
                />
              </AppField>

              <AppField
                label="SMTP 服务器与端口"
                description="465 走 SSL，587 走 STARTTLS"
                :error="errorOf('MAIL', 'port') ?? errorOf('MAIL', 'host')"
              >
                <AppInput
                  :model-value="draft['MAIL.host'] ?? ''"
                  :numeric="false"
                  :invalid="Boolean(errorOf('MAIL', 'host'))"
                  placeholder="smtp.qq.com"
                  @update:model-value="(value) => (draft['MAIL.host'] = value)"
                />
                <AppInput
                  :model-value="draft['MAIL.port'] ?? ''"
                  class="app-port"
                  :invalid="Boolean(errorOf('MAIL', 'port'))"
                  inputmode="numeric"
                  @update:model-value="(value) => (draft['MAIL.port'] = value)"
                />
              </AppField>

              <AppField
                label="账号"
                description="发件邮箱账号，缺省时与收件地址相同"
                :error="errorOf('MAIL', 'username')"
              >
                <AppInput
                  :model-value="draft['MAIL.username'] ?? ''"
                  :numeric="false"
                  inputmode="email"
                  @update:model-value="(value) => (draft['MAIL.username'] = value)"
                />
              </AppField>

              <AppField label="授权码" description="输入后点保存写入，任何页面都不会再显示它">
                <div class="app-secret">
                  <span class="app-secret__mask">••••••••••••</span>
                  <span
                    class="app-secret__chip"
                    :class="{ 'app-secret__chip--off': !mail.passwordConfigured }"
                  >
                    {{ mail.passwordConfigured ? '已配置' : '未配置' }}
                  </span>
                  <button type="button" class="app-secret__btn" @click="focusPassword">更换</button>
                </div>
              </AppField>

              <AppField label="新授权码" description="留空表示不修改">
                <AppInput
                  v-model="newPassword"
                  type="password"
                  :numeric="false"
                  placeholder="输入新的授权码"
                  data-password-input
                />
                <label class="app-clear">
                  <input v-model="clearPassword" type="checkbox" />
                  清除已保存
                </label>
              </AppField>
            </AppGroup>

            <div class="app-notice">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="12" cy="12" r="9.2" />
                <path d="M12 11v5.4M12 7.6v.2" />
              </svg>
              <div class="app-notice__tx">
                <b>生效范围</b>
                　红利与网格按标的保存，改动只影响这一只；指标与警示参数影响午盘页的指标状态与提醒；邮件参数在下一个收件时点生效。改动在保存成功后开始使用，不回填当日已发出的邮件。
                <span v-if="effectiveNote" class="app-notice__note">{{ effectiveNote }}</span>
              </div>
            </div>
          </div>
        </main>

        <aside class="app-aside">
          <AppAsideBlock title="本次改动">
            <template #count>
              <span v-if="changes.length" class="app-count">{{ changes.length }}</span>
            </template>
            <div class="app-diff">
              <div v-if="changes.length === 0" class="app-diff__empty">还没有改动</div>
              <div v-for="(change, index) in changes" :key="index" class="app-diff__row">
                <div class="app-diff__head">
                  <span class="app-diff__label">{{ change.label }}</span>
                  <span v-if="change.invalid" class="app-diff__bad">校验失败</span>
                </div>
                <div class="app-diff__vals">
                  <span class="app-diff__old">{{ change.from }}</span>
                  <span class="app-diff__arw">→</span>
                  <span class="app-diff__new" :class="{ 'app-diff__new--bad': change.invalid }">
                    {{ change.to }}
                  </span>
                </div>
              </div>
            </div>
          </AppAsideBlock>

          <AppAsideBlock title="校验清单">
            <template #count>
              <span class="app-count">{{ checks.length }}</span>
            </template>
            <div class="app-checks">
              <div
                v-for="(check, index) in checks"
                :key="index"
                class="app-check"
                :class="{ 'app-check--fail': !check.pass }"
              >
                <svg
                  v-if="check.pass"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="m5 12.5 4.5 4.5L19 7" />
                </svg>
                <svg
                  v-else
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                >
                  <circle cx="12" cy="12" r="9.2" />
                  <path d="M12 7.5v5.2M12 16.3v.2" />
                </svg>
                <span>{{ check.label }}</span>
              </div>
            </div>
          </AppAsideBlock>

          <AppAsideBlock title="邮件通道">
            <template #count>
              <span v-if="mail.testable" class="app-count">可测试</span>
            </template>
            <div class="app-mailbox" :class="{ 'app-mailbox--off': !mail.ready }">
              <b>{{ mail.ready ? '通道就绪' : '通道未就绪' }}</b>
              {{ mail.note || '尚未配置 SMTP 参数' }}
            </div>
          </AppAsideBlock>
        </aside>
      </div>
    </div>

    <AppSaveBar
      v-if="
        activeGroup === 'BOND' ||
        activeGroup === 'INDICATOR' ||
        activeGroup === 'ALERT' ||
        activeGroup === 'MAIL'
      "
      :error-count="errorCount"
      :first-error="firstError"
      :dirty-count="dirtyCount"
      :last-saved-at="lastSavedAt"
      :saving="saving"
      primary-label="保存全部"
      kbd-hint="⌘S"
      @save="saveAll"
      @discard="discard"
    />

    <MessageHost />
  </div>
</template>

<script setup lang="ts">
/**
 * 参数设置页：红利与网格先选标的再配它自己的参数，债券/指标/警示是全局参数
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AppButton from '@/ui/AppButton.vue'
import AppField from '@/ui/AppField.vue'
import AppGroup from '@/ui/AppGroup.vue'
import AppInput from '@/ui/AppInput.vue'
import AppSwitch from '@/ui/AppSwitch.vue'
import AppSaveBar from '@/layout/AppSaveBar.vue'
import AppAsideBlock from '@/layout/AppAsideBlock.vue'
import AppPulseBar from '@/layout/AppPulseBar.vue'
import AppSideRail from '@/layout/AppSideRail.vue'
import AppTopBar from '@/layout/AppTopBar.vue'
import MessageHost from '@/ui/MessageHost.vue'
import { ElMessage } from '@/ui/message'
import {
  mailSetting as mailSettingApi,
  overview as overviewApi,
  reset as resetApi,
  save as saveApi,
  saveMailSetting as saveMailApi,
} from '@/api/settingController'
import { list as listBondApi } from '@/api/bondController'
import {
  watch as dividendWatchApi,
  savePlan as saveDividendPlanApi,
  removePlan as removeDividendPlanApi,
} from '@/api/dividendAdviceController'
import {
  watch as gridWatchApi,
  planOf as gridPlanOfApi,
  savePlan as saveGridPlanApi,
  removePlan as removeGridPlanApi,
} from '@/api/gridController'
import {
  refresh as refreshDividendApi,
  manual as saveDividendManualApi,
} from '@/api/indexDividendController'

/** 红利监控对象，直接用生成层的类型 */
type DividendWatch = API.DividendWatchVO

/** 网格监控对象，直接用生成层的类型 */
type GridWatch = API.GridWatchVO

/** 待保存的分组，values 是「参数键 → 参数值」的键值表 */
interface SettingGroupPayload {
  group: string
  values: Record<string, string>
}

/** 阶梯一档的草稿，数值都按字符串存以复用输入框 */
interface RungDraft {
  /** 股息率门槛，百分点 */
  yield: string
  /** 该档应持有金额，元 */
  amount: string
}

/** 一只基金的红利阶梯草稿，按股息率降序 */
interface DividendDraft {
  ladder: RungDraft[]
}

/** 一只标的的网格参数草稿 */
interface GridDraft {
  lowerBound: string
  upperBound: string
  step: string
  amountPerGrid: string
}

/** 手工录入股息率的草稿 */
interface ManualDraft {
  dataDate: string
  dividendYield: string
  indexName: string
}

/** 分组图标，用内联 SVG 以免引入图标库 */
const GROUP_ICONS: Record<string, string> = {
  DIVIDEND:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v18M5 8l7-5 7 5M5 16l7 5 7-5"/></svg>',
  GRID: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M3 12h18M3 18h18M8 3v18M16 3v18"/></svg>',
  BOND: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 17c3-3 6 3 9 0s6-3 9 0"/><path d="M3 11h18"/><circle cx="7" cy="6" r="1.4"/><circle cx="17" cy="6" r="1.4"/></svg>',
  INDICATOR:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 17l5-6 4 3 4-7 5 4"/></svg>',
  ALERT:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4.5 4 9v6l8 4.5 8-4.5V9z"/><path d="M12 4.5V20"/></svg>',
  MAIL: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="m4 7.5 8 5.5 8-5.5"/></svg>',
  WATCH:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M2.5 12s3.6-6.5 9.5-6.5S21.5 12 21.5 12s-3.6 6.5-9.5 6.5S2.5 12 2.5 12z"/><circle cx="12" cy="12" r="2.6"/></svg>',
}

/** 当前激活的侧栏项 */
const activeGroup = ref('DIVIDEND')

/** 页面加载中 */
const loading = ref(true)

/** 保存中 */
const saving = ref(false)

/** 分组数据，来自生成的 SettingGroupVO */
const groups = ref<API.SettingGroupVO[]>([])

/** 编辑中的值，键为「分组.字段」 */
const draft = reactive<Record<string, string>>({})

/** 服务端的原始值，用于算脏值与改动对照 */
const baseline = reactive<Record<string, string>>({})

/** 字段级错误，键为「分组.字段」 */
const errors = reactive<Record<string, string>>({})

/** 顶层错误，接口整体失败时展示 */
const globalError = ref('')

/** 邮件设置 */
const mail = reactive<API.MailSettingVO & { testable?: boolean }>({})

/** 新授权码，只写不回显，保存后立即清空 */
const newPassword = ref('')

/** 是否要求清除授权码 */
const clearPassword = ref(false)

/** 最后保存时间 */
const lastSavedAt = ref('')

/** 生效说明 */
const effectiveNote = ref('')

/** 红利监控对象 */
const dividendWatch = ref<DividendWatch[]>([])

/** 当前选中的红利基金 */
const selectedDividend = ref('')

/** 选中基金的完整信息，含股息率与它自己的参数 */
const dividendDetail = ref<DividendWatch | null>(null)

/** 红利阶梯草稿，按股息率降序 */
const dividendDraft = reactive<DividendDraft>({ ladder: [] })

/** 阶梯的校验问题，逐条显示在阶梯下方 */
const ladderErrors = ref<string[]>([])

/** 红利保存中 */
const dividendSaving = ref(false)

/** 红利保存成功提示 */
const dividendSavedNote = ref('')

/** 股息率刷新中 */
const refreshing = ref(false)

/** 手工录入面板是否展开 */
const manualOpen = ref(false)

/** 手工录入草稿 */
const manualDraft = reactive<ManualDraft>({ dataDate: '', dividendYield: '', indexName: '' })

/** 手工录入提交中 */
const manualSaving = ref(false)

/** 手工录入的阻断说明，如未登记跟踪指数 */
const manualBlocked = ref('')

/** 网格监控对象 */
const gridWatch = ref<GridWatch[]>([])

/** 当前选中的网格标的 */
const selectedGrid = ref('')

/** 选中标的的完整信息 */
const gridDetail = ref<GridWatch | null>(null)

/** 网格参数草稿 */
const gridDraft = reactive<GridDraft>({
  lowerBound: '',
  upperBound: '',
  step: '',
  amountPerGrid: '',
})

/** 网格字段级错误，键为字段名 */
const gridErrors = reactive<Record<string, string>>({})

/** 网格保存中 */
const gridSaving = ref(false)

/** 网格保存成功提示 */
const gridSavedNote = ref('')

/** 网格线序列，保存后从接口读回 */
const gridLines = ref<number[]>([])

/** 债券监控对象，只用来说明这组参数管几只 */
const bondWatch = ref<API.BondMonitorVO[]>([])

/** 参数分组名称集合，只有全局分组走 /setting 接口 */
const PARAM_GROUPS = ['BOND', 'INDICATOR', 'ALERT'] as const

/** 邮件普通字段的名称集合 */
const MAIL_FIELDS = ['recipients', 'host', 'port', 'username', 'from'] as const

/** 侧栏锚点，数量按分组实际持仓数给 */
const railItems = computed(() => [
  {
    key: 'DIVIDEND',
    label: '红利策略',
    icon: GROUP_ICONS.DIVIDEND!,
    count: dividendWatch.value.length,
  },
  { key: 'GRID', label: '网格策略', icon: GROUP_ICONS.GRID!, count: gridWatch.value.length },
  { key: 'BOND', label: '债券监控', icon: GROUP_ICONS.BOND!, count: 6 },
  { key: 'INDICATOR', label: '技术指标', icon: GROUP_ICONS.INDICATOR!, count: 10 },
  { key: 'ALERT', label: '警示参数', icon: GROUP_ICONS.ALERT!, count: 3 },
  { key: 'MAIL', label: '邮件与 SMTP', icon: GROUP_ICONS.MAIL!, count: 5 },
])

/** 侧栏总数，供顶部行情条显示 */
const railTotal = computed(() => dividendWatch.value.length + gridWatch.value.length)

/** 当前显示的分组；MAIL 不在 groups 里，单独由邮件区块渲染 */
const activeGroupMeta = computed(() =>
  groups.value.find((item) => item.group === activeGroup.value),
)

/** 能正常参与建议的红利基金数 */
const usableCount = computed(() => dividendWatch.value.filter((row) => row.usable !== false).length)

/** 红利的阻断项，用于分组右上角状态 */
const dividendBlocked = computed(() => dividendWatch.value.filter((row) => row.usable === false))

/** 网格的阻断项，未配置也算阻断，因为它确实不会提醒 */
const gridBlocked = computed(() =>
  gridWatch.value.filter((row) => row.customized === false || row.paramUsable === false),
)

/** 选中基金是否已有保存过的参数 */
const hasDividendParam = computed(() => Boolean(dividendDetail.value?.paramUsable))

/** 缺股息率时的说明，把「刷新」与「手工」两条路说清 */
const fixHint = computed(() => {
  const detail = dividendDetail.value
  if (!detail) {
    return ''
  }
  if (!detail.indexCode) {
    return '这只基金没有登记跟踪指数，先到持仓页把指数补上，否则取不到任何股息率'
  }
  return `股息率来自跟踪指数 ${detail.indexCode}。先试自动抓取，抓不到（国证系列常见）时用手工录入，数据日期必须如实填写`
})

/** 键拼装 */
function keyOf(group: string, field: string): string {
  return `${group}.${field}`
}

/** 按分组取字段列表 */
function itemsOf(group: string): API.SettingItemVO[] {
  return groups.value.find((item) => item.group === group)?.items ?? []
}

/** 脏值数量，只算全局分组与邮件 */
const dirtyCount = computed(() => {
  let count = 0
  Object.keys(draft).forEach((key) => {
    if (draft[key] !== baseline[key]) {
      count++
    }
  })
  if (newPassword.value.trim() || clearPassword.value) {
    count++
  }
  return count
})

/** 错误条数 */
const errorCount = computed(() => Object.keys(errors).length)

/** 第一条错误文案 */
const firstError = computed(() => Object.values(errors)[0] ?? '')

/** 改动清单，用于右栏对照 */
const changes = computed(() => {
  const list: Array<{ label: string; from: string; to: string; invalid: boolean }> = []
  groups.value.forEach((group) => {
    ;(group.items ?? []).forEach((item) => {
      const key = keyOf(group.group ?? '', item.key ?? '')
      if (draft[key] !== undefined && draft[key] !== baseline[key]) {
        list.push({
          label: item.label ?? '',
          from: baseline[key] || '（未设置）',
          to: draft[key] || '（空）',
          invalid: Boolean(errors[key]),
        })
      }
    })
  })
  return list
})

/** 成对读出阶梯草稿的数值，非法值给 0 便于逐项判断 */
const parsedLadder = computed(() =>
  dividendDraft.ladder.map((row) => ({
    // 空串不能当 0：否则空行会被判成「股息率 0」，单调性校验全部失真
    yield: Number(row.yield),
    amount: Number(row.amount),
    filled: row.yield.trim() !== '' && row.amount.trim() !== '',
  })),
)

/** 按股息率降序排好的阶梯，用于预览 */
const ladderPreview = computed(() =>
  parsedLadder.value
    .filter((row) => row.filled && Number.isFinite(row.yield) && Number.isFinite(row.amount))
    .map((row) => ({ yield: row.yield, amount: row.amount }))
    .sort((left, right) => right.yield - left.yield),
)

/** 当前股息率命中的那一档，用于告诉使用者「现在该持多少」 */
const hitPreview = computed(() => {
  const rows = ladderPreview.value
  const yieldValue = dividendDetail.value?.dividendYield
  if (!rows.length || yieldValue == null) {
    return null
  }
  return rows.find((row) => yieldValue >= row.yield) ?? rows[rows.length - 1] ?? null
})

/** 校验清单，页面右侧逐项显示 */
const checks = computed(() => {
  const list: Array<{ label: string; pass: boolean }> = []
  if (activeGroup.value === 'DIVIDEND') {
    const rows = parsedLadder.value
    const filled = rows.filter((row) => row.filled)
    list.push({
      label: '至少两档且都已填',
      pass: filled.length >= 2 && filled.length === rows.length,
    })
    list.push({
      label: '股息率逐档递减',
      pass:
        filled.length >= 2 && filled.every((row, i) => i === 0 || row.yield < filled[i - 1]!.yield),
    })
    list.push({
      label: '金额随股息率递减',
      pass:
        filled.length >= 2 &&
        filled.every((row, i) => i === 0 || row.amount < filled[i - 1]!.amount),
    })
    list.push({ label: '最低档即保底，金额为正', pass: (filled.at(-1)?.amount ?? 0) > 0 })
  } else if (activeGroup.value === 'GRID') {
    const lower = Number(gridDraft.lowerBound)
    const upper = Number(gridDraft.upperBound)
    const step = Number(gridDraft.step)
    list.push({ label: '上下界都为正数', pass: lower > 0 && upper > 0 })
    list.push({ label: '上界 > 下界', pass: Number.isFinite(upper) && upper > lower })
    list.push({
      label: '至少能划出两格',
      pass: Number.isFinite(step) && step > 0 && (upper - lower) / step >= 2,
    })
    list.push({
      label: '每格金额为正数',
      pass: dividendDraftFloorSafe(gridDraft.amountPerGrid),
    })
  } else {
    list.push({ label: 'SMTP 授权码已配置', pass: Boolean(mail.passwordConfigured) })
  }
  return list
})

/**
 * 判断字符串是否是正数，用于校验清单
 *
 * @param value 文本
 * @returns 是正数返回 true
 */
function dividendDraftFloorSafe(value: string): boolean {
  const number = Number(value)
  return Number.isFinite(number) && number > 0
}

/** 把草稿与基线一起写入，未自定义的字段基线留空以便识别脏值 */
function seed(next: API.SettingGroupVO[]): void {
  groups.value = next
  Object.keys(draft).forEach((key) => delete draft[key])
  Object.keys(baseline).forEach((key) => delete baseline[key])
  next.forEach((group) => {
    ;(group.items ?? []).forEach((item) => {
      const key = keyOf(group.group ?? '', item.key ?? '')
      draft[key] = item.value ?? ''
      baseline[key] = item.customized ? (item.value ?? '') : ''
    })
  })
}

/** 拉取页面数据 */
async function load(): Promise<void> {
  loading.value = true
  globalError.value = ''
  try {
    const overviewResult = await overviewApi()
    const overview = overviewResult.data ?? {}
    seed(overview.groups ?? [])
    effectiveNote.value = overview.effectiveNote ?? ''
    const mailResult = await mailSettingApi()
    const mailSetting = mailResult.data ?? {}
    Object.assign(mail, mailSetting)
    MAIL_FIELDS.forEach((field) => {
      const key = `MAIL.${field}`
      draft[key] = mailSetting[field] ?? ''
      baseline[key] = mailSetting[field] ?? ''
    })
  } catch {
    // 出错时保留上次成功结果，不白屏
    globalError.value = '读取参数失败，页面显示的是上次成功的结果'
  } finally {
    loading.value = false
  }
}

/** 收集待保存的分组，只提交变过的字段 */
function collectPayload(): SettingGroupPayload[] {
  const payload: SettingGroupPayload[] = []
  PARAM_GROUPS.forEach((groupName) => {
    const values: Record<string, string> = {}
    itemsOf(groupName).forEach((item) => {
      const key = keyOf(groupName, item.key ?? '')
      if (draft[key] !== baseline[key]) {
        values[item.key ?? ''] = draft[key] ?? ''
      }
    })
    if (Object.keys(values).length > 0) {
      payload.push({ group: groupName, values })
    }
  })
  return payload
}

/** 邮件字段是否有改动 */
function mailChanged(): boolean {
  const fieldChanged = MAIL_FIELDS.some(
    (field) => (draft[`MAIL.${field}`] ?? '') !== (baseline[`MAIL.${field}`] ?? ''),
  )
  return fieldChanged || Boolean(newPassword.value.trim()) || clearPassword.value
}

/** 组装邮件保存入参 */
function buildMailPayload(): API.MailSettingSaveRequest {
  return {
    recipients: draft['MAIL.recipients'],
    host: draft['MAIL.host'],
    port: draft['MAIL.port'],
    username: draft['MAIL.username'],
    from: draft['MAIL.from'],
    ...(newPassword.value.trim() ? { password: newPassword.value.trim() } : {}),
    ...(clearPassword.value ? { clearPassword: true } : {}),
  }
}

/** 保存全局参数与邮件，任一失败即标记失败并保留错误 */
async function saveAll(): Promise<void> {
  Object.keys(errors).forEach((key) => delete errors[key])
  const payload = collectPayload()
  const mailDirty = mailChanged()
  if (payload.length === 0 && !mailDirty) {
    return
  }

  saving.value = true
  try {
    let failed = false
    if (payload.length > 0) {
      // 生成层把 Map<String,String> 映射成了 { key?: string }，真实语义是键值表，此处转一次
      const body = {
        groups: payload.map((item) => ({
          group: item.group,
          values: item.values as unknown as API.MapString,
        })),
      }
      const saveResult = await saveApi(body as API.SettingBatchSaveRequest)
      const result = saveResult.data ?? {}
      if (result.ok) {
        payload.forEach((group) => {
          Object.keys(group.values).forEach((field) => {
            const key = keyOf(group.group, field)
            baseline[key] = draft[key] ?? ''
          })
        })
      } else {
        failed = true
        collectErrors(result.errors ?? [])
      }
    }
    if (mailDirty) {
      const mailSaveResult = await saveMailApi(buildMailPayload())
      const mailResult = mailSaveResult.data ?? {}
      if (mailResult.ok) {
        const refreshedResult = await mailSettingApi()
        Object.assign(mail, refreshedResult.data ?? {})
        MAIL_FIELDS.forEach((field) => {
          const key = `MAIL.${field}`
          baseline[key] = draft[key] ?? ''
        })
        newPassword.value = ''
        clearPassword.value = false
      } else {
        failed = true
        collectErrors(mailResult.errors ?? [])
      }
    }
    if (!failed) {
      lastSavedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
      await load()
    }
  } finally {
    saving.value = false
  }
}

/** 把字段级错误摊平到 errors，无字段名的归到顶层 */
function collectErrors(list: API.SettingFieldError[]): void {
  list.forEach((item) => {
    if (item.field) {
      errors[keyOf(item.group ?? '', item.field)] = item.message ?? '参数不合法'
    } else {
      globalError.value = item.message ?? '参数不合法'
    }
  })
}

/** 放弃修改，回到基线 */
function discard(): void {
  Object.keys(draft).forEach((key) => {
    if (baseline[key] !== undefined) {
      draft[key] = baseline[key]
    }
  })
  newPassword.value = ''
  clearPassword.value = false
  Object.keys(errors).forEach((key) => delete errors[key])
}

/** 恢复单个分组默认值 */
async function resetOne(group: string): Promise<void> {
  const result = await resetApi({ group })
  if (result.data?.ok) {
    await load()
  }
}

/** 键盘快捷键，Cmd/Ctrl + S 触发保存 */
function onKeydown(event: KeyboardEvent): void {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void saveAll()
  }
}

/** 切换分组，左栏点击即整页切换而不是页内滚动 */
function scrollTo(group: string): void {
  activeGroup.value = group
}

/** 输入框的错误提示 */
function errorOf(group: string, field: string): string | undefined {
  return errors[keyOf(group, field)]
}

/** 字段值读写 */
function valueOf(group: string, field: string): string {
  return draft[keyOf(group, field)] ?? ''
}

/** 写入字段值 */
function setValue(group: string, field: string, value: string): void {
  draft[keyOf(group, field)] = value
}

/** 点「更换」时聚焦授权码输入框 */
function focusPassword(): void {
  clearPassword.value = false
  const element = document.querySelector<HTMLInputElement>('input[data-password-input]')
  element?.focus()
}

/** 数值展示，缺值给破折号 */
function fmt(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

/** 金额展示，两位小数 */
function fmtMoney(value?: number | null): string {
  if (value === undefined || value === null) {
    return '—'
  }
  return Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/**
 * 数值转输入框文本
 *
 * @param value 数值
 * @returns 文本，缺值返回空串
 */
function toText(value?: number | null): string {
  return value === undefined || value === null ? '' : String(value)
}

/**
 * 红利监控对象的状态标签文字
 *
 * @param row 监控对象
 * @returns 标签文字
 */
function dividendTagText(row: DividendWatch): string {
  if (row.paramUsable === false) {
    return hasRowParam(row) ? '参数不全' : '未配置'
  }
  if (row.dividendYield == null) {
    return '缺股息率'
  }
  if (row.stale) {
    return '数据过期'
  }
  return row.actionLabel ?? '正常'
}

/**
 * 红利监控对象的状态标签样式
 *
 * @param row 监控对象
 * @returns 样式类
 */
function dividendTagClass(row: DividendWatch): string {
  if (row.paramUsable === false) {
    return 'app-pick__tag--warn'
  }
  if (row.dividendYield == null || row.stale) {
    return 'app-pick__tag--warn'
  }
  return 'app-pick__tag--ok'
}

/**
 * 网格监控对象的状态标签文字
 *
 * @param row 监控对象
 * @returns 标签文字
 */
function gridTagText(row: GridWatch): string {
  if (row.customized === false) {
    return '未配置'
  }
  if (row.paramUsable === false) {
    return '参数不全'
  }
  if (row.position === 'ABOVE') {
    return '高于上界'
  }
  if (row.position === 'BELOW') {
    return '低于下界'
  }
  return '正常'
}

/**
 * 网格监控对象的状态标签样式
 *
 * @param row 监控对象
 * @returns 样式类
 */
function gridTagClass(row: GridWatch): string {
  if (row.customized === false || row.paramUsable === false) {
    return 'app-pick__tag--warn'
  }
  return row.position === 'INSIDE' ? 'app-pick__tag--ok' : 'app-pick__tag--warn'
}

/**
 * 判断这只基金是否存过阶梯，用于区分「未配置」与「配了但有问题」
 *
 * @param row 监控对象
 * @returns 存过阶梯返回 true
 */
function hasRowParam(row: DividendWatch): boolean {
  return Boolean(row.ladder?.length)
}

/**
 * 拉取红利与网格的监控对象，参数按标的各存一套，列表本身就是选择器
 *
 * @returns 无
 */
async function loadWatch(): Promise<void> {
  const [dividendResult, gridResult, bondResult] = await Promise.allSettled([
    dividendWatchApi(),
    gridWatchApi(),
    listBondApi(),
  ])
  if (dividendResult.status === 'fulfilled') {
    dividendWatch.value = dividendResult.value.data ?? []
    // 选中项被清仓后在列表里消失时，回退到第一只，避免详情区停在已失效的对象上
    const stillThere = dividendWatch.value.some((row) => row.varietyKey === selectedDividend.value)
    if (!stillThere) {
      const first = dividendWatch.value[0]
      selectedDividend.value = first?.varietyKey ?? ''
      if (first) {
        fillDividendDetail(first)
      } else {
        dividendDetail.value = null
      }
    } else {
      const current = dividendWatch.value.find((row) => row.varietyKey === selectedDividend.value)
      if (current) {
        fillDividendDetail(current)
      }
    }
  }
  if (gridResult.status === 'fulfilled') {
    gridWatch.value = gridResult.value.data ?? []
    const stillThere = gridWatch.value.some((row) => row.varietyKey === selectedGrid.value)
    if (!stillThere) {
      const first = gridWatch.value[0]
      selectedGrid.value = first?.varietyKey ?? ''
      if (first) {
        fillGridDetail(first)
      } else {
        gridDetail.value = null
      }
    } else {
      const current = gridWatch.value.find((row) => row.varietyKey === selectedGrid.value)
      if (current) {
        fillGridDetail(current)
      }
    }
  }
  if (bondResult.status === 'fulfilled') {
    bondWatch.value = [
      ...(bondResult.value.data?.items ?? []),
      ...(bondResult.value.data?.idleItems ?? []),
    ]
  }
}

/**
 * 把监控对象映射进红利详情与草稿，已有参数才填，没配过时留空等使用者填
 *
 * @param row 监控对象
 * @returns 无
 */
function fillDividendDetail(row: DividendWatch): void {
  dividendDetail.value = row
  ladderErrors.value = []
  dividendSavedNote.value = ''
  manualOpen.value = false
  manualBlocked.value = ''
  manualDraft.dataDate = ''
  manualDraft.dividendYield = ''
  manualDraft.indexName = row.indexName ?? ''
  const ladder = row.ladder ?? []
  dividendDraft.ladder = ladder.map((rung) => ({
    yield: toText(rung.yield),
    amount: toText(rung.amount),
  }))
}

/**
 * 从一个空的三档骨架开始，不预填任何数字
 *
 * @returns 三档空草稿
 */
function blankLadder(): RungDraft[] {
  return [
    { yield: '', amount: '' },
    { yield: '', amount: '' },
    { yield: '', amount: '' },
  ]
}

/**
 * 追加一档，从最高档往上加；空骨架时先给三行
 *
 * @returns 无
 */
function addRung(): void {
  const rows = dividendDraft.ladder
  if (!rows.length) {
    dividendDraft.ladder = blankLadder()
    return
  }
  const top = rows[0]
  if (!top) {
    return
  }
  rows.unshift({ yield: '', amount: '' })
}

/**
 * 删除最低一档，保底随之上移
 *
 * @returns 无
 */
function removeRung(): void {
  if (dividendDraft.ladder.length > 2) {
    dividendDraft.ladder.pop()
  }
}

/**
 * 删除指定档位
 *
 * @param index 下标
 * @returns 无
 */
function removeRungAt(index: number): void {
  if (dividendDraft.ladder.length > 2) {
    dividendDraft.ladder.splice(index, 1)
  }
}

/**
 * 把监控对象映射进网格详情与草稿
 *
 * @param row 监控对象
 * @returns 无
 */
function fillGridDetail(row: GridWatch): void {
  gridDetail.value = row
  Object.keys(gridErrors).forEach((key) => delete gridErrors[key])
  gridSavedNote.value = ''
  gridDraft.lowerBound = toText(row.lowerBound)
  gridDraft.upperBound = toText(row.upperBound)
  gridDraft.step = toText(row.step)
  gridDraft.amountPerGrid = toText(row.amountPerGrid)
  void loadGridLines(row.varietyKey ?? '')
}

/**
 * 切换选中的红利基金，detail 直接来自列表，不再额外请求参数接口
 *
 * @param row 监控对象
 * @returns 无
 */
function selectDividend(row: DividendWatch): void {
  selectedDividend.value = row.varietyKey ?? ''
  fillDividendDetail(row)
}

/**
 * 切换选中的网格标的，顺带把它的网格线读回来
 *
 * @param row 监控对象
 * @returns 无
 */
function selectGrid(row: GridWatch): void {
  selectedGrid.value = row.varietyKey ?? ''
  fillGridDetail(row)
}

/**
 * 读回某只标的的网格线，供保存后直接看划格结果
 *
 * @param varietyKey 品种业务主键
 * @returns 无
 */
async function loadGridLines(varietyKey: string): Promise<void> {
  gridLines.value = []
  if (!varietyKey) {
    return
  }
  try {
    const plan = await gridPlanOfApi({ varietyKey })
    gridLines.value = plan.data?.lines ?? []
  } catch {
    // 读不到网格线不影响编辑，只是少一段展示
    gridLines.value = []
  }
}

/**
 * 取网格字段错误
 *
 * @param field 字段名
 * @returns 错误文案，无错误返回空串
 */
function gridError(field: string): string {
  return gridErrors[field] ?? ''
}

/**
 * 保存当前这只基金的红利参数，校验失败就地标红，不提交到后端
 *
 * @returns 无
 */
async function saveDividendRow(): Promise<void> {
  const target = dividendDetail.value
  if (!target?.varietyKey) {
    return
  }
  ladderErrors.value = []
  const rows = parsedLadder.value
  if (!rows.length) {
    ladderErrors.value.push('还没有任何一档，请先填至少两档')
    return
  }
  if (rows.some((row) => !row.filled)) {
    ladderErrors.value.push('有档位没填完，股息率与应持有金额都要填')
    return
  }
  if (rows.length < 2) {
    ladderErrors.value.push('至少要两档，否则表达不了越跌越买')
  }
  rows.forEach((row, index) => {
    if (!Number.isFinite(row.yield) || row.yield <= 0 || row.yield > 100) {
      ladderErrors.value.push(`第 ${index + 1} 档的股息率应在 0~100 之间`)
    }
    if (!Number.isFinite(row.amount) || row.amount <= 0) {
      ladderErrors.value.push(`第 ${index + 1} 档的应持有金额必须为正数`)
    }
    if (index > 0 && row.yield >= rows[index - 1]!.yield) {
      ladderErrors.value.push(`第 ${index + 1} 档的股息率必须低于上一档`)
    }
    if (index > 0 && row.amount >= rows[index - 1]!.amount) {
      // 阶梯按股息率降序排列，所以越往下金额必须越少；写反了就成了越跌越卖
      ladderErrors.value.push(`第 ${index + 1} 档的应持有金额必须低于上一档（股息率越低持有越少）`)
    }
  })
  if (ladderErrors.value.length > 0) {
    return
  }
  dividendSaving.value = true
  try {
    await saveDividendPlanApi({
      varietyKey: target.varietyKey,
      ladder: rows.map((row) => ({ yield: row.yield, amount: row.amount })),
    })
    dividendSavedNote.value = `已保存 ${target.varietyName ?? target.varietyKey} 的阶梯`
    ElMessage.success('股息率阶梯已保存')
    await loadWatch()
  } catch (error) {
    // 后端的校验文案更具体，原样接到阶梯下方，不只弹一条笼统提示
    const message = error instanceof Error ? error.message : ''
    if (message) {
      ladderErrors.value.push(message)
    }
  } finally {
    dividendSaving.value = false
  }
}

/**
 * 清除当前这只基金的红利参数
 *
 * @returns 无
 */
async function clearDividendRow(): Promise<void> {
  const target = dividendDetail.value
  if (!target?.varietyKey) {
    return
  }
  await removeDividendPlanApi({ varietyKey: target.varietyKey })
  ElMessage.success('已清除这只的红利参数')
  await loadWatch()
}

/**
 * 刷新当前这只基金跟踪指数的股息率
 *
 * @param row 监控对象
 * @returns 无
 */
async function refreshDividendOf(row: DividendWatch): Promise<void> {
  if (!row.indexCode) {
    ElMessage.error('这只基金未登记跟踪指数，先到持仓页补上指数再来刷新')
    return
  }
  refreshing.value = true
  try {
    const result = await refreshDividendApi([row.indexCode])
    const item = result.data?.items?.[0]
    if (item?.saved) {
      ElMessage.success(`已写入 ${row.indexCode} 的股息率，数据日期 ${item.dataDate ?? '—'}`)
    } else {
      // 抓不到时把原因摆出来，并把手工录入入口提到眼前
      manualOpen.value = true
      ElMessage.error(item?.message ?? '自动抓取没有取到数据，请手工录入')
    }
    await loadWatch()
  } finally {
    refreshing.value = false
  }
}

/**
 * 打开或收起手工录入面板，未登记指数时拦下并说明
 *
 * @param row 监控对象
 * @returns 无
 */
function openManual(row: DividendWatch): void {
  if (!row.indexCode) {
    manualBlocked.value = '未登记跟踪指数，手工录入也存不到正确的指数上'
    manualOpen.value = false
    return
  }
  manualBlocked.value = ''
  manualOpen.value = !manualOpen.value
  manualDraft.indexName = row.indexName ?? ''
}

/**
 * 提交手工录入的股息率，数据日期必须由使用者如实填写
 *
 * @returns 无
 */
async function submitManual(): Promise<void> {
  const target = dividendDetail.value
  if (!target?.indexCode) {
    manualBlocked.value = '未登记跟踪指数，无法确定股息率属于哪只指数'
    return
  }
  if (!manualDraft.dataDate) {
    ElMessage.error('必须填写数据日期，否则无法判断是否过期')
    return
  }
  const yieldValue = Number(manualDraft.dividendYield)
  if (!Number.isFinite(yieldValue) || yieldValue <= 0 || yieldValue > 100) {
    ElMessage.error('股息率应在 0~100 的百分点区间内，如 4.94 表示 4.94%')
    return
  }
  manualSaving.value = true
  try {
    await saveDividendManualApi({
      indexCode: target.indexCode,
      dataDate: manualDraft.dataDate,
      dividendYield: yieldValue,
      ...(manualDraft.indexName ? { indexName: manualDraft.indexName } : {}),
    })
    ElMessage.success('股息率已写入')
    manualOpen.value = false
    await loadWatch()
  } finally {
    manualSaving.value = false
  }
}

/**
 * 保存当前这只标的的网格参数，校验失败就地标红
 *
 * @returns 无
 */
async function saveGridRow(): Promise<void> {
  const target = gridDetail.value
  if (!target?.varietyKey) {
    return
  }
  Object.keys(gridErrors).forEach((key) => delete gridErrors[key])
  const lower = Number(gridDraft.lowerBound)
  const upper = Number(gridDraft.upperBound)
  const step = Number(gridDraft.step)
  const amount = Number(gridDraft.amountPerGrid)
  if (!Number.isFinite(lower) || lower <= 0) {
    gridErrors.lowerBound = '下界必须为正数'
  }
  if (!Number.isFinite(upper) || upper <= 0) {
    gridErrors.upperBound = '上界必须为正数'
  } else if (Number.isFinite(lower) && upper <= lower) {
    gridErrors.upperBound = '上界必须大于下界'
  }
  if (!Number.isFinite(step) || step <= 0) {
    gridErrors.step = '格子大小必须为正数'
  }
  if (!Number.isFinite(amount) || amount <= 0) {
    gridErrors.amountPerGrid = '每格金额必须为正数'
  }
  if (Object.keys(gridErrors).length > 0) {
    return
  }
  gridSaving.value = true
  try {
    const result = await saveGridPlanApi({
      varietyKey: target.varietyKey,
      lowerBound: lower,
      upperBound: upper,
      step,
      amountPerGrid: amount,
    })
    const lines = result.data?.lines ?? []
    gridLines.value = lines
    gridSavedNote.value = `已保存，可划 ${lines.length} 条网格线`
    ElMessage.success(`${target.varietyName ?? target.varietyKey} 的格子已保存`)
    await loadWatch()
  } catch (error) {
    // 后端也会校验，把它的问题逐条摊到字段上，避免只弹一条笼统提示
    const message = error instanceof Error ? error.message : ''
    if (message.includes('下界')) {
      gridErrors.lowerBound = message
    } else if (message.includes('上界')) {
      gridErrors.upperBound = message
    } else if (message.includes('格子')) {
      gridErrors.step = message
    } else if (message.includes('每格金额')) {
      gridErrors.amountPerGrid = message
    }
  } finally {
    gridSaving.value = false
  }
}

/**
 * 清除当前这只标的的网格参数
 *
 * @returns 无
 */
async function clearGridRow(): Promise<void> {
  const target = gridDetail.value
  if (!target?.varietyKey) {
    return
  }
  await removeGridPlanApi({ varietyKey: target.varietyKey })
  ElMessage.success('已清除这只的格子')
  await loadWatch()
}

watch(
  () => dividendDraft.ladder.map((row) => `${row.yield}|${row.amount}`).join(','),
  () => {
    // 改过阶梯就清掉上一次的报错，避免已修好的问题一直挂着
    ladderErrors.value = []
  },
)

onMounted(() => {
  void load()
  void loadWatch()
  window.addEventListener('keydown', onKeydown)
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
  padding: 26px 26px 118px;
}

.app-center__in {
  max-width: var(--content);
  margin: 0 auto;
}

.app-aside {
  align-self: stretch;
  min-height: calc(100vh - var(--topbar) - var(--pulse));
  padding: 26px 18px 118px;
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

  .app-center__in {
    max-width: 860px;
  }
}

@media (max-width: 860px) {
  .app-body {
    padding-top: var(--topbar);
    padding-left: 0;
  }

  .app-center {
    padding: 20px 16px 110px;
  }
}

.app-unit {
  flex: none;
  font-size: 11px;
  color: var(--t4);
}

/* ── 标的列表：本身就是选择器 ── */
.app-pick {
  display: grid;
  gap: 7px;
  padding: 12px 0 4px;
}

.app-pick__item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--hair);
  border-radius: 9px;
  background: var(--s1);
  box-shadow: var(--sh-sm);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}

.app-pick__item:hover {
  border-color: var(--hair-2);
  background: var(--s2);
}

.app-pick__item--on {
  border-color: var(--acc-line);
  background: var(--acc-soft);
}

.app-pick__nm {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  font-weight: 550;
  color: var(--t1);
}

.app-pick__cd {
  flex: none;
  font-variant-numeric: tabular-nums;
  font-size: 10.5px;
  color: var(--t4);
}

.app-pick__tag {
  flex: none;
  margin-left: auto;
  padding: 1px 7px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 650;
  white-space: nowrap;
}

.app-pick__tag--ok {
  background: var(--dn-soft);
  color: var(--dn);
}

.app-pick__tag--warn {
  background: color-mix(in oklab, var(--warn) 14%, transparent);
  color: var(--warn);
}

/* ── 选中标的的详情 ── */
.app-spec {
  margin-top: 12px;
  padding: 11px 14px;
  border: 1px solid var(--hair);
  border-radius: 10px;
  background: var(--s2);
}

.app-spec__row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 5px 0;
  font-size: 11.5px;
}

.app-spec__k {
  flex: none;
  width: 74px;
  color: var(--t4);
}

.app-spec__v {
  min-width: 0;
  color: var(--t2);
  line-height: 1.6;
}

.app-spec__v--num {
  font-variant-numeric: tabular-nums;
  color: var(--t1);
}

.app-spec__v--warn {
  color: var(--warn);
}

.app-spec__sub {
  display: block;
  margin-top: 2px;
  font-size: 10.5px;
  color: var(--t4);
}

.app-spec__pill {
  margin-left: 6px;
}

.app-spec__missing {
  color: var(--warn);
}

.app-spec__why {
  margin: 6px 0 0;
  padding-left: 16px;
  font-size: 10.5px;
  line-height: 1.65;
  color: var(--warn);
}

/* ── 缺股息率时的补数区 ── */
.app-fix {
  margin-top: 10px;
  padding: 12px 14px;
  border: 1px solid var(--acc-line);
  border-radius: 10px;
  background: var(--acc-soft);
}

.app-fix__bar {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 11.5px;
  color: var(--t2);
}

.app-fix__bar b {
  font-weight: 650;
  color: var(--t1);
}

.app-fix__note {
  font-size: 11px;
  line-height: 1.6;
  color: var(--t3);
}

.app-fix__acts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 9px;
}

.app-fix__blocked {
  font-size: 11px;
  color: var(--acc);
}

.app-manual {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--hair);
}

.app-manual__f {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 10.5px;
  color: var(--t4);
}

.app-manual__in {
  height: 31px;
  padding: 0 9px;
  border: 1px solid var(--hair-2);
  border-radius: 7px;
  background: var(--inp-bg);
  color: var(--t1);
  font: inherit;
  font-size: 12px;
  outline: none;
}

.app-manual__in--num {
  width: 96px;
  font-variant-numeric: tabular-nums;
}

.app-manual__in:focus {
  border-color: var(--acc);
  box-shadow: 0 0 0 3px var(--acc-glow);
}

/* ── 逐只参数表单 ── */
.app-dividend-form {
  margin-top: 4px;
}

/* ── 股息率阶梯 ── */
.app-ladder {
  margin-top: 12px;
  padding: 13px 14px;
  border: 1px solid var(--hair);
  border-radius: 10px;
  background: var(--s2);
}

.app-ladder__head {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 11px;
}

.app-ladder__head b {
  display: block;
  font-size: 12.5px;
  font-weight: 650;
  color: var(--t1);
}

.app-ladder__tip {
  display: block;
  max-width: 560px;
  margin-top: 3px;
  font-size: 10.5px;
  line-height: 1.6;
  color: var(--t4);
}

.app-ladder__acts {
  display: flex;
  flex: none;
  gap: 7px;
  margin-left: auto;
}

.app-ladder__grid {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.app-ladder__empty {
  padding: 14px 12px;
  border: 1px dashed var(--hair-2);
  border-radius: 8px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--t3);
}

.app-ladder__empty-tip {
  display: block;
  margin-top: 4px;
  font-size: 10.5px;
  color: var(--t4);
}

.app-ladder__row {
  display: flex;
  align-items: flex-end;
  gap: 9px;
}

.app-ladder__no {
  flex: none;
  width: 34px;
  padding-bottom: 8px;
  font-size: 10px;
  font-weight: 650;
  color: var(--t4);
}

.app-ladder__f {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10.5px;
  color: var(--t4);
}

.app-ladder__in {
  width: 72px;
  height: 31px;
  padding: 0 8px;
  border: 1px solid var(--hair-2);
  border-radius: 7px;
  background: var(--inp-bg);
  color: var(--t1);
  font: inherit;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  outline: none;
}

.app-ladder__in--wide {
  width: 104px;
}

.app-ladder__in:focus {
  border-color: var(--acc);
  box-shadow: 0 0 0 3px var(--acc-glow);
}

.app-ladder__u {
  font-size: 10.5px;
  color: var(--t4);
}

.app-ladder__arw {
  padding-bottom: 9px;
  font-size: 10px;
  color: var(--t4);
}

.app-ladder__del {
  margin-bottom: 5px;
  padding: 2px 6px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--t4);
  font: inherit;
  font-size: 10.5px;
  cursor: pointer;
}

.app-ladder__del:hover {
  background: var(--s3);
  color: var(--acc);
}

.app-ladder__preview {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid var(--hair);
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  color: var(--t3);
}

.app-ladder__pv {
  white-space: nowrap;
}

.app-ladder__err {
  margin: 9px 0 0;
  padding-left: 16px;
  font-size: 10.5px;
  line-height: 1.65;
  color: var(--acc);
}

.app-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  padding: 13px 0 4px;
}

.app-actions__picked {
  font-size: 11px;
  color: var(--dn);
}

.app-actions__lines {
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  color: var(--t4);
}

.app-hint {
  margin: 8px 0 6px;
  padding: 9px 12px;
  border-radius: 8px;
  background: var(--s2);
  font-size: 10.5px;
  line-height: 1.65;
  color: var(--t3);
}

.app-pill {
  display: inline-flex;
  align-items: center;
  height: 19px;
  padding: 0 7px;
  border-radius: 4px;
  background: var(--s3);
  color: var(--t3);
  font-size: 10px;
  font-weight: 650;
  white-space: nowrap;
}

.app-pill--warn {
  background: color-mix(in oklab, var(--warn) 14%, transparent);
  color: var(--warn);
}

.app-listempty {
  padding: 18px 0;
  text-align: center;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--t4);
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
  max-width: 640px;
  margin-top: 9px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--t3);
}

.app-loading {
  padding: 64px 0;
  text-align: center;
  font-size: 12.5px;
  color: var(--t4);
}

.app-alert {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid var(--acc-line);
  border-radius: 10px;
  background: var(--acc-soft);
  color: var(--acc);
  font-size: 12px;
}

.app-alert svg {
  flex: none;
  width: 14px;
  height: 14px;
  margin-top: 1px;
}

/* ── 生效说明 ── */
.app-notice {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 12px;
  padding: 15px 16px;
  border-radius: 11px;
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
}

.app-notice svg {
  flex: none;
  width: 14px;
  height: 14px;
  margin-top: 1px;
  color: var(--acc);
}

.app-notice__tx {
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--t2);
}

.app-notice__tx b {
  font-weight: 650;
  color: var(--t1);
}

.app-notice__note {
  display: block;
  margin-top: 4px;
  color: var(--t4);
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
  letter-spacing: 0;
}

.app-diff {
  overflow: hidden;
  border: 1px solid var(--hair);
  border-radius: 11px;
  background: var(--s1);
  box-shadow: var(--group-hi), var(--sh-sm);
}

.app-diff__empty {
  padding: 16px 12px;
  text-align: center;
  font-size: 11.5px;
  color: var(--t4);
}

.app-diff__row {
  padding: 10px 12px;
  border-bottom: 1px solid var(--hair);
  font-size: 11.5px;
}

.app-diff__row:last-child {
  border-bottom: 0;
}

.app-diff__head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 5px;
  color: var(--t3);
}

.app-diff__label {
  font-weight: 500;
  color: var(--t2);
}

.app-diff__bad {
  padding: 0 5px;
  border-radius: 3px;
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
  color: var(--acc);
  font-size: 10px;
  font-weight: 650;
}

.app-diff__vals {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  font-variant-numeric: tabular-nums;
}

.app-diff__old {
  color: var(--diff-del);
  text-decoration: line-through;
  opacity: 0.8;
}

.app-diff__arw {
  color: var(--t4);
  font-size: 10px;
}

.app-diff__new {
  font-weight: 600;
  color: var(--diff-add);
}

.app-diff__new--bad {
  color: var(--acc);
}

.app-checks {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.app-check {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--t3);
}

.app-check svg {
  flex: none;
  width: 13px;
  height: 13px;
  margin-top: 1px;
  stroke: var(--dn);
  fill: none;
}

.app-check--fail {
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
  color: var(--acc);
}

.app-check--fail svg {
  stroke: var(--acc);
}

.app-mailbox {
  padding: 12px 14px;
  border-radius: 11px;
  background: var(--dn-soft);
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--t2);
}

.app-mailbox--off {
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
}

.app-mailbox b {
  display: block;
  margin-bottom: 4px;
  font-weight: 650;
  color: var(--t1);
}

.app-port {
  max-width: 92px;
}

.app-secret {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 8px;
  height: 36px;
  padding: 0 6px 0 12px;
  border: 1px solid var(--hair-2);
  border-radius: 8px;
  background: var(--s2);
}

.app-secret__mask {
  flex: 1;
  font-size: 12.5px;
  letter-spacing: 0.26em;
  color: var(--t4);
}

.app-secret__chip {
  flex: none;
  padding: 1.5px 7px;
  border-radius: 4px;
  background: var(--dn-soft);
  box-shadow: inset 0 0 0 1px var(--dn-soft);
  color: var(--dn);
  font-size: 10px;
  font-weight: 700;
}

.app-secret__chip--off {
  background: var(--acc-soft);
  box-shadow: inset 0 0 0 1px var(--acc-line);
  color: var(--acc);
}

.app-secret__btn {
  flex: none;
  height: 27px;
  padding: 0 10px;
  border: 1px solid var(--hair-2);
  border-radius: 6px;
  background: var(--btn-bg);
  box-shadow: var(--sh-sm);
  color: var(--t2);
  font: inherit;
  font-size: 11.5px;
  cursor: pointer;
  transition: 0.15s;
}

.app-secret__btn:hover {
  border-color: var(--hair-3);
  background: var(--btn-hover-bg);
  color: var(--t1);
}

.app-clear {
  display: flex;
  align-items: center;
  flex: none;
  gap: 6px;
  font-size: 11px;
  color: var(--t4);
  cursor: pointer;
  white-space: nowrap;
}

.app-clear input {
  accent-color: var(--acc);
}
</style>
