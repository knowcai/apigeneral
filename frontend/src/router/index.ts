import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import DatasourceView from '../views/DatasourceView.vue'
import ApiView from '../views/ApiView.vue'
import LogView from '../views/LogView.vue'
import PolicyView from '../views/PolicyView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/datasources',
      children: [
        { path: 'datasources', component: DatasourceView },
        { path: 'apis', component: ApiView },
        { path: 'logs', component: LogView },
        { path: 'policy', component: PolicyView }
      ]
    }
  ]
})

export default router
