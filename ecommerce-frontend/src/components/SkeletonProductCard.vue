<template>
  <div class="skel-card">
    <div class="skel-img"></div>
    <div class="skel-line w-80"></div>
    <div class="skel-line w-40"></div>
  </div>
</template>
<style scoped>
.skel-card { padding: 16px; }
.skel-img { border-radius: 12px; background: var(--color-border-subtle); aspect-ratio: 1; animation: shimmer 1.5s infinite; background-size: 200% 100%; background-image: linear-gradient(90deg, var(--color-border-subtle) 25%, var(--color-border) 50%, var(--color-border-subtle) 75%); }
.skel-line { height: 14px; border-radius: 4px; margin-top: 10px; background: var(--color-border-subtle); animation: shimmer 1.5s infinite; background-size: 200% 100%; background-image: linear-gradient(90deg, var(--color-border-subtle) 25%, var(--color-border) 50%, var(--color-border-subtle) 75%); }
.w-80 { width: 80%; } .w-40 { width: 40%; }
@keyframes shimmer { 0% { background-position: -200% 0; } 100% { background-position: 200% 0; } }
</style>
