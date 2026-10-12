<template>
  <div class="scene-root">
    <svg
      ref="svgRef"
      class="sc"
      :class="sceneClasses"
      viewBox="0 0 560 268"
      role="img"
      aria-label="四位店员组成的欢迎场景：一位举着相机，一位端着三明治，一位在遥控小机器人，一位对着镜子展示衣服"
      fill="none"
      stroke="var(--color-text-primary)"
      stroke-width="1.6"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <!-- ── 1 号：拿相机 ── -->
      <g class="ch ch--1" data-face="80,120">
        <g class="ch-rise">
          <g class="ch-bob">
            <ellipse class="shadow" cx="80" cy="236" rx="33" ry="5" />
            <path class="limb" d="M71 198V222M89 198V222" />
            <rect class="body-fill" x="58" y="146" width="44" height="52" rx="18" />
            <path class="limb" d="M62 166L56 184M98 166L104 184" />
            <g class="cam">
              <rect class="prop prop--orange" x="62" y="164" width="36" height="22" rx="6" />
              <rect class="prop prop--orange-deep" x="70" y="160" width="12" height="6" rx="2" />
              <circle class="lens" cx="80" cy="175" r="6.5" />
            </g>
            <circle class="flash" cx="80" cy="175" r="14" />
            <circle class="head-fill" cx="80" cy="120" r="25" />
            <g class="face">
              <circle class="eye-white" cx="72" cy="117" />
              <circle class="eye-white" cx="88" cy="117" />
              <circle class="pupil" cx="72" cy="117" />
              <circle class="pupil" cx="88" cy="117" />
            </g>
            <circle class="cheek" cx="64" cy="128" r="4.5" />
            <circle class="cheek" cx="96" cy="128" r="4.5" />
            <path class="mouth" d="M73 129q7 6 14 0" />
            <path class="mouth-happy" d="M73 128q7 9 14 0z" />
            <path class="mouth-sad" d="M73 133q7-6 14 0" />
          </g>
        </g>
      </g>

      <!-- ── 2 号：端三明治 ── -->
      <g class="ch ch--2" data-face="200,120">
        <g class="ch-rise">
          <g class="ch-bob">
            <ellipse class="shadow" cx="200" cy="236" rx="33" ry="5" />
            <path class="limb" d="M191 198V222M209 198V222" />
            <rect class="body-fill" x="178" y="146" width="44" height="52" rx="18" />
            <path class="limb" d="M182 166L176 184M218 166L224 184" />
            <g>
              <path class="prop prop--sand" d="M184 170l16-10 16 10z" />
              <rect class="prop prop--green" x="185" y="170" width="30" height="6" rx="2" />
              <rect class="prop prop--cream" x="184" y="176" width="32" height="7" rx="3" />
            </g>
            <circle class="head-fill" cx="200" cy="120" r="25" />
            <g class="face">
              <circle class="eye-white" cx="192" cy="117" />
              <circle class="eye-white" cx="208" cy="117" />
              <circle class="pupil" cx="192" cy="117" />
              <circle class="pupil" cx="208" cy="117" />
            </g>
            <circle class="cheek" cx="184" cy="128" r="4.5" />
            <circle class="cheek" cx="216" cy="128" r="4.5" />
            <path class="mouth" d="M193 129q7 6 14 0" />
            <path class="mouth-happy" d="M193 128q7 9 14 0z" />
            <path class="mouth-sad" d="M193 133q7-6 14 0" />
          </g>
        </g>
      </g>

      <!-- ── 3 号：遥控小机器人 ── -->
      <g class="ch ch--3" data-face="320,120">
        <g class="ch-rise">
          <g class="ch-bob">
            <ellipse class="shadow" cx="320" cy="236" rx="33" ry="5" />
            <path class="limb" d="M311 198V222M329 198V222" />
            <rect class="body-fill" x="298" y="146" width="44" height="52" rx="18" />
            <path class="limb" d="M302 166L292 182M338 168L356 180" />
            <g>
              <rect class="prop prop--ink" x="304" y="162" width="15" height="24" rx="5" />
              <circle class="remote-dot" cx="311.5" cy="170" r="2.2" />
              <circle class="remote-dot-dim" cx="311.5" cy="178" r="2.2" />
            </g>
            <circle class="head-fill" cx="320" cy="120" r="25" />
            <g class="face">
              <circle class="eye-white" cx="312" cy="117" />
              <circle class="eye-white" cx="328" cy="117" />
              <circle class="pupil" cx="312" cy="117" />
              <circle class="pupil" cx="328" cy="117" />
            </g>
            <circle class="cheek" cx="304" cy="128" r="4.5" />
            <circle class="cheek" cx="336" cy="128" r="4.5" />
            <path class="mouth" d="M313 129q7 6 14 0" />
            <path class="mouth-happy" d="M313 128q7 9 14 0z" />
            <path class="mouth-sad" d="M313 133q7-6 14 0" />
          </g>
        </g>

        <!-- 小机器人：密码聚焦时醒来 -->
        <g class="mini-robot">
          <ellipse class="shadow" cx="386" cy="230" rx="21" ry="4" />
          <path class="limb robot-antenna" d="M386 188V178" />
          <circle class="antenna-tip" cx="386" cy="175" r="3" />
          <rect class="body-fill" x="371" y="188" width="30" height="32" rx="11" />
          <circle class="robot-eye" cx="380" cy="203" r="3.2" />
          <circle class="robot-eye" cx="392" cy="203" r="3.2" />
          <path class="limb" d="M378 212h16" />
        </g>
      </g>

      <!-- ── 4 号：对着落地镜展示衣服 ── -->
      <g class="ch ch--4" data-face="452,120">
        <g class="ch-rise">
          <g class="ch-bob">
            <ellipse class="shadow" cx="452" cy="236" rx="33" ry="5" />
            <path class="limb" d="M443 198V222M461 198V222" />
            <rect class="body-fill" x="430" y="146" width="44" height="52" rx="18" />
            <path class="limb" d="M434 166L426 184M470 164L484 150" />
            <circle class="head-fill" cx="452" cy="120" r="25" />
            <g class="face">
              <circle class="eye-white" cx="444" cy="117" />
              <circle class="eye-white" cx="460" cy="117" />
              <circle class="pupil" cx="444" cy="117" />
              <circle class="pupil" cx="460" cy="117" />
            </g>
            <circle class="cheek" cx="436" cy="128" r="4.5" />
            <circle class="cheek" cx="468" cy="128" r="4.5" />
            <path class="mouth" d="M445 129q7 6 14 0" />
            <path class="mouth-happy" d="M445 128q7 9 14 0z" />
            <path class="mouth-sad" d="M445 133q7-6 14 0" />
          </g>
        </g>

        <!-- 落地镜 -->
        <g class="mirror">
          <ellipse class="mirror__glass" cx="516" cy="146" rx="29" ry="43" />
          <path class="limb" d="M516 189V216" />
          <ellipse class="shadow" cx="516" cy="220" rx="19" ry="4" />
          <path class="mirror__gloss" d="M500 122l14 62" />
        </g>

        <!-- 举着的衣服：在镜子之后渲染，形成「举到镜子前」的层次 -->
        <g>
          <rect class="prop prop--green-soft" x="474" y="130" width="34" height="28" rx="8" />
          <path class="prop-line" d="M491 130v28" />
          <path class="prop-line" d="M479 135l-5 6M503 135l5 6" />
        </g>
      </g>

      <!-- ── 成功时的彩纸 ── -->
      <g class="confetti-group">
        <rect class="confetti confetti--orange" x="80" y="58" width="6" height="9" rx="1.5" />
        <rect class="confetti confetti--green" x="130" y="44" width="6" height="9" rx="1.5" />
        <rect class="confetti confetti--sand" x="180" y="62" width="6" height="9" rx="1.5" />
        <rect class="confetti confetti--orange" x="230" y="46" width="6" height="9" rx="1.5" />
        <rect class="confetti confetti--deep" x="280" y="60" width="6" height="9" rx="1.5" />
        <rect class="confetti confetti--green" x="330" y="42" width="6" height="9" rx="1.5" />
        <rect class="confetti confetti--orange" x="380" y="58" width="6" height="9" rx="1.5" />
        <rect class="confetti confetti--sand" x="430" y="48" width="6" height="9" rx="1.5" />
        <rect class="confetti confetti--deep" x="480" y="64" width="6" height="9" rx="1.5" />
        <rect class="confetti confetti--green" x="520" y="45" width="6" height="9" rx="1.5" />
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps<{
  formState: 'idle' | 'focus-username' | 'focus-password' | 'submitting' | 'success' | 'error'
}>()

const svgRef = ref<SVGSVGElement | null>(null)

const sceneClasses = computed(() => ({
  'state-idle': props.formState === 'idle',
  'state-focus-username': props.formState === 'focus-username',
  'state-focus-password': props.formState === 'focus-password',
  'state-submitting': props.formState === 'submitting',
  'state-success': props.formState === 'success',
  'state-error': props.formState === 'error',
}))

/* ── 瞳孔跟随鼠标 ───────────────────────────────────────── */
function onMouseMove(e: MouseEvent) {
  const svg = svgRef.value
  if (!svg) return

  const box = svg.getBoundingClientRect()
  if (!box.width) return

  // 屏幕坐标 → SVG viewBox 坐标
  const vb = svg.viewBox.baseVal
  const mx = (e.clientX - box.left) * (vb.width / box.width) + vb.x
  const my = (e.clientY - box.top) * (vb.height / box.height) + vb.y

  svg.querySelectorAll<SVGGElement>('.ch[data-face]').forEach((ch) => {
    const raw = ch.getAttribute('data-face') || '0,0'
    const [fx, fy] = raw.split(',').map(Number)
    const dx = mx - fx
    const dy = my - fy
    const dist = Math.sqrt(dx * dx + dy * dy)

    let ox = 0
    let oy = 0
    if (dist > 24) {
      // 幅度控制在瞳孔不跑出眼白
      ox = (dx / dist) * 2.2
      oy = (dy / dist) * 1.6
    }
    ch.querySelectorAll<SVGCircleElement>('.pupil').forEach((p) => {
      p.style.transform = `translate(${ox.toFixed(2)}px,${oy.toFixed(2)}px)`
    })
  })

  // 镜面高光跟着光标挪一点，暗示镜子在反光
  const gloss = svg.querySelector<SVGPathElement>('.mirror__gloss')
  if (gloss) {
    const gx = Math.max(-4, Math.min(4, (mx - 516) / 26))
    gloss.style.transform = `translateX(${gx.toFixed(2)}px)`
  }
}

/* ── 随机眨眼，让待机有生命感 ───────────────────────────── */
let blinkTimer: number | undefined

function blink() {
  const svg = svgRef.value
  if (!svg) return
  svg.classList.add('is-blinking')
  window.setTimeout(() => svg.classList.remove('is-blinking'), 130)
  blinkTimer = window.setTimeout(blink, 2200 + Math.random() * 2800)
}

onMounted(() => {
  window.addEventListener('mousemove', onMouseMove, { passive: true })
  blinkTimer = window.setTimeout(blink, 1500)
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove)
  if (blinkTimer) window.clearTimeout(blinkTimer)
})
</script>

<style scoped>
/* 本地色板：项目全局没有这套 token，这里自带一份，避免污染其它页面 */
.scene-root {
  --sc-ink: var(--color-text-primary);
  --sc-cream: var(--color-bg-overlay);
  --sc-raised: var(--color-bg-raised);
  --sc-orange: var(--color-accent);
  --sc-orange-deep: var(--color-accent-dark);
  --sc-green: #788c5d;
  --sc-sand: #c4b99a;

  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: visible;
}

.sc {
  width: 100%;
  height: auto;
  max-height: 100%;
  overflow: visible;
}

/* ── 基础笔触 ── */
.limb {
  stroke: var(--sc-ink);
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  fill: none;
}
.body-fill,
.head-fill {
  fill: var(--sc-cream);
  stroke: var(--sc-ink);
  stroke-width: 1.6;
}
.shadow {
  fill: var(--sc-ink);
  opacity: 0.07;
}
.eye-white {
  r: 5.6px;
  fill: var(--sc-cream);
  stroke: var(--sc-ink);
  stroke-width: 1.2;
}
.pupil {
  r: 3.3px;
  fill: var(--sc-ink);
}
.cheek {
  fill: var(--sc-orange);
  opacity: 0.28;
}
.mouth,
.mouth-sad {
  stroke: var(--sc-ink);
  stroke-width: 1.6;
  stroke-linecap: round;
  fill: none;
}
.mouth-happy {
  fill: var(--sc-ink);
  stroke: none;
}
.mouth-happy,
.mouth-sad {
  opacity: 0;
}

/* ── 道具配色 ── */
.prop {
  stroke: var(--sc-ink);
  stroke-width: 1.4;
}
.prop--orange { fill: var(--sc-orange); }
.prop--orange-deep { fill: var(--sc-orange-deep); }
.prop--green { fill: var(--sc-green); }
.prop--green-soft { fill: var(--sc-green); opacity: 0.92; }
.prop--sand { fill: var(--sc-sand); }
.prop--cream { fill: var(--sc-cream); }
.prop--ink { fill: var(--sc-ink); }
.prop-line {
  stroke: var(--sc-ink);
  stroke-width: 1.2;
  opacity: 0.45;
  fill: none;
}
.lens {
  fill: var(--sc-cream);
  stroke: var(--sc-ink);
  stroke-width: 1.4;
}
.remote-dot { fill: var(--sc-orange); }
.remote-dot-dim { fill: var(--sc-cream); opacity: 0.7; }
.antenna-tip { fill: var(--sc-orange); }

.mirror__glass {
  fill: var(--sc-raised);
  stroke: var(--sc-ink);
  stroke-width: 2.5;
}
.mirror__gloss {
  stroke: var(--sc-cream);
  stroke-width: 6;
  stroke-linecap: round;
  fill: none;
  transition: transform 120ms linear;
}

.robot-eye {
  fill: var(--color-text-muted);
  transition: fill 250ms ease;
}

/* ── 三层结构：状态位移 / 入场 / 待机呼吸，互不覆盖 ── */
.ch {
  transition: transform 400ms cubic-bezier(0.16, 1, 0.3, 1);
  transform-box: fill-box;
  transform-origin: center bottom;
}
.ch--2 { transition-delay: 40ms; }
.ch--3 { transition-delay: 80ms; }
.ch--4 { transition-delay: 120ms; }

.ch-rise {
  animation: chRise 700ms cubic-bezier(0.16, 1, 0.3, 1) both;
  transform-box: fill-box;
  transform-origin: center bottom;
}
.ch--1 .ch-rise { animation-delay: 60ms; }
.ch--2 .ch-rise { animation-delay: 150ms; }
.ch--3 .ch-rise { animation-delay: 240ms; }
.ch--4 .ch-rise { animation-delay: 330ms; }
@keyframes chRise {
  from { opacity: 0; transform: translateY(26px) scale(0.94); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

.ch-bob {
  animation: idleBob 3.6s ease-in-out infinite;
  transform-box: fill-box;
  transform-origin: center bottom;
}
.ch--2 .ch-bob { animation-delay: -0.9s; }
.ch--3 .ch-bob { animation-delay: -1.8s; }
.ch--4 .ch-bob { animation-delay: -2.7s; }
@keyframes idleBob {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-3.5px); }
}

/* ── 瞳孔跟随 + 眨眼 ── */
.pupil {
  transform-box: fill-box;
  transform-origin: center;
  transition: transform 120ms linear;
}
.eye-white,
.pupil {
  transform-box: fill-box;
  transform-origin: center;
}
.is-blinking .eye-white,
.is-blinking .pupil {
  transform: scaleY(0.12);
}

/* ── 1. 聚焦用户名：1 号举相机 + 闪光 ── */
.cam {
  transform-box: fill-box;
  transform-origin: center;
  transition: transform 250ms cubic-bezier(0.16, 1, 0.3, 1);
}
.flash {
  fill: var(--sc-orange-deep);
  opacity: 0;
  transform-box: fill-box;
  transform-origin: center;
  transform: scale(0);
}
.state-focus-username .ch--1 { transform: translateY(-4px); }
.state-focus-username .cam { transform: translateY(-6px) rotate(-6deg); }
.state-focus-username .flash { animation: camFlash 520ms cubic-bezier(0.16, 1, 0.3, 1); }
@keyframes camFlash {
  0%   { transform: scale(0.2); opacity: 0; }
  35%  { transform: scale(1); opacity: 0.95; }
  100% { transform: scale(1.9); opacity: 0; }
}

/* ── 2. 聚焦密码：3 号的小机器人醒来 ── */
.mini-robot {
  transform-box: fill-box;
  transform-origin: center bottom;
  transition: transform 250ms cubic-bezier(0.34, 1.56, 0.64, 1);
}
.robot-antenna {
  transform-box: fill-box;
  transform-origin: center bottom;
  transition: transform 250ms cubic-bezier(0.34, 1.56, 0.64, 1);
}
.state-focus-password .ch--3 { transform: translateY(-3px); }
.state-focus-password .mini-robot { transform: translateY(-8px); }
.state-focus-password .robot-antenna { transform: scaleY(1.25); }
.state-focus-password .robot-eye { fill: var(--sc-orange); }

/* ── 3. 提交中：依次轻跳 ── */
.state-submitting .ch-bob {
  animation: chHop 900ms cubic-bezier(0.16, 1, 0.3, 1) infinite;
}
.state-submitting .ch--2 .ch-bob { animation-delay: 120ms; }
.state-submitting .ch--3 .ch-bob { animation-delay: 240ms; }
.state-submitting .ch--4 .ch-bob { animation-delay: 360ms; }
@keyframes chHop {
  0%, 100% { transform: translateY(0); }
  40%      { transform: translateY(-7px); }
}

/* ── 4. 成功：笑脸 + 彩纸 ── */
.state-success .mouth { opacity: 0; }
.state-success .mouth-happy { opacity: 1; }
.state-success .ch--1 { transform: translateY(-6px); }
.state-success .ch--3 { transform: translateY(-6px); }

.confetti {
  transform-box: fill-box;
  transform-origin: center;
  opacity: 0;
}
.confetti--orange { fill: var(--sc-orange); }
.confetti--deep { fill: var(--sc-orange-deep); }
.confetti--green { fill: var(--sc-green); }
.confetti--sand { fill: var(--sc-sand); }

.state-success .confetti { animation: confettiFall 1200ms cubic-bezier(0.16, 1, 0.3, 1) forwards; }
.state-success .confetti:nth-of-type(1) { animation-delay: 0ms; }
.state-success .confetti:nth-of-type(2) { animation-delay: 70ms; }
.state-success .confetti:nth-of-type(3) { animation-delay: 140ms; }
.state-success .confetti:nth-of-type(4) { animation-delay: 45ms; }
.state-success .confetti:nth-of-type(5) { animation-delay: 190ms; }
.state-success .confetti:nth-of-type(6) { animation-delay: 110ms; }
.state-success .confetti:nth-of-type(7) { animation-delay: 230ms; }
.state-success .confetti:nth-of-type(8) { animation-delay: 30ms; }
.state-success .confetti:nth-of-type(9) { animation-delay: 165ms; }
.state-success .confetti:nth-of-type(10) { animation-delay: 95ms; }
@keyframes confettiFall {
  0%   { transform: translateY(-14px) scale(0.6) rotate(0deg); opacity: 0; }
  25%  { opacity: 1; }
  100% { transform: translateY(58px) scale(1) rotate(160deg); opacity: 0; }
}

/* ── 5. 出错：全员撇嘴 ── */
.state-error .mouth { opacity: 0; }
.state-error .mouth-sad { opacity: 1; }
.state-error .ch--2 { transform: translateY(2px) rotate(-3deg); }

/* ── 无障碍：尊重系统「减少动态效果」 ── */
@media (prefers-reduced-motion: reduce) {
  .ch-rise,
  .ch-bob,
  .state-submitting .ch-bob,
  .state-success .confetti,
  .state-focus-username .flash {
    animation: none !important;
  }
  .ch-rise { opacity: 1; }
}
</style>
