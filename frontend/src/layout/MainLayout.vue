<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">SQL API Gateway</div>
      <el-menu :default-active="route.path" router class="side-menu">
        <el-menu-item index="/datasources">
          <el-icon><Connection /></el-icon>
          <span>连接串管理</span>
        </el-menu-item>
        <el-menu-item index="/apis">
          <el-icon><Document /></el-icon>
          <span>API / SQL</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><DataLine /></el-icon>
          <span>访问监控</span>
        </el-menu-item>
        <el-menu-item index="/policy">
          <el-icon><Setting /></el-icon>
          <span>限流熔断</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<style scoped>
.layout {
  height: 100vh;
  background: #fff;
}

.aside {
  background: #000;
  color: #fff;
  border-right: 1px solid #000;
}

.logo {
  padding: 24px 20px;
  font-weight: 600;
  font-size: 14px;
  color: #fff;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  border-bottom: 1px solid #333;
}

.side-menu {
  border-right: none;
  background: #000;
}

:deep(.el-menu-item) {
  color: #a3a3a3;
  border-left: 3px solid transparent;
}

:deep(.el-menu-item:hover) {
  background: #171717 !important;
  color: #fff;
}

:deep(.el-menu-item.is-active) {
  background: #fff !important;
  color: #000 !important;
  border-left-color: #fff;
  font-weight: 600;
}

:deep(.el-menu-item.is-active .el-icon) {
  color: #000;
}

.main {
  background: #fff;
  padding: 32px 40px;
}
</style>
