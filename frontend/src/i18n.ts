import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import HttpBackend from 'i18next-http-backend'
import LanguageDetector from 'i18next-browser-languagedetector'

i18n
  .use(HttpBackend)
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    fallbackLng: 'en',
    supportedLngs: ['en'],
    defaultNS: 'common',
    ns: [
      'common',
      'nav',
      'schedule',
      'shiftDetail',
      'newShift',
      'dashboard',
      'clients',
      'caregivers',
      'payers',
      'evvStatus',
      'auth',
    ],
    backend: {
      loadPath: '/locales/{{lng}}/{{ns}}.json',
    },
    interpolation: {
      escapeValue: false, // React handles XSS escaping
    },
  })

export default i18n
