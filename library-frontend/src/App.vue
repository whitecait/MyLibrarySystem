<template>
  <main class="status-page">
    <section class="status-card" aria-labelledby="status-title">
      <p class="eyebrow">MyLibrarySystem</p>
      <h1 id="status-title">工程基础准备中</h1>
      <p>当前仅提供可构建、可测试的最小应用骨架，业务功能将在需求基线批准后实现。</p>
      <dl>
        <div>
          <dt>前端</dt>
          <dd>Vue 3 / Vite</dd>
        </div>
        <div>
          <dt>后端</dt>
          <dd>Spring Boot / Java 21</dd>
        </div>
        <div>
          <dt>数据库</dt>
          <dd>PostgreSQL / Flyway</dd>
        </div>
      </dl>
    </section>
  </main>
</template>

<style scoped>
:global(*) {
  box-sizing: border-box;
}

:global(body) {
  margin: 0;
  color: #162033;
  background: #edf3f7;
  font-family:
    Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

.status-page {
  display: grid;
  min-height: 100vh;
  padding: 2rem;
  place-items: center;
}

.status-card {
  width: min(42rem, 100%);
  padding: clamp(2rem, 5vw, 4rem);
  border: 1px solid #cad8e3;
  border-radius: 1.25rem;
  background: #fff;
  box-shadow: 0 1.5rem 4rem rgb(43 72 94 / 12%);
}

.eyebrow {
  margin: 0 0 0.75rem;
  color: #176b87;
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: clamp(2rem, 7vw, 3.5rem);
  line-height: 1.08;
}

.status-card > p:not(.eyebrow) {
  margin: 1.25rem 0 2rem;
  color: #526274;
  line-height: 1.7;
}

dl {
  display: grid;
  gap: 0.75rem;
  margin: 0;
}

dl div {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid #e2eaf0;
}

dt {
  font-weight: 700;
}

dd {
  margin: 0;
  color: #526274;
  text-align: right;
}
</style>
