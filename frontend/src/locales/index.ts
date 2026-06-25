import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

const saved = localStorage.getItem('gw-locale')
const locale = saved === 'en-US' ? 'en-US' : 'zh-CN'

export const i18n = createI18n({
  legacy: false,
  locale,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  }
})

export function setLocale(lang: 'zh-CN' | 'en-US') {
  i18n.global.locale.value = lang
  localStorage.setItem('gw-locale', lang)
}
