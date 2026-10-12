<template>
  <div class="skel-order">
    <div class="skel-line w-60"></div>
    <div class="skel-line w-90"></div>
    <div class="skel-line w-50"></div>
    <div class="skel-line w-30"></div>
  </div>
</template>
<style scoped>
.skel-order { border: 1px solid var(--color-border); border-radius: 12px; padding: 16px; margin-bottom: 12px; }
.skel-line { height: 14px; border-radius: 4px; margin-bottom: 8px; background: var(--color-border-subtle); animation: shimmer 1.5s infinite; background-size: 200% 100%; background-image: linear-gradient(90deg, var(--color-border-subtle) 25%, var(--color-border) 50%, var(--color-border-subtle) 75%); }
.w-60 { width: 60%; } .w-90 { width: 90%; } .w-50 { width: 50%; } .w-30 { width: 30%; }
@keyframes shimmer { 0% { background-position: -200% 0; } 100% { background-position: 200% 0; } }
</style>
