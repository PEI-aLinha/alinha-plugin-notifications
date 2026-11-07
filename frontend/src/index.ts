import { definePlugin } from '@alinha/plugin-sdk'
import { NotificationsPage } from './pages/NotificationsPage'

/**
 * Ponto de entrada do plugin no frontend. O shell lê este objeto e monta
 * as rotas e o menu — não há nada a registar à mão no shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /carteira/<path>
 */
export default definePlugin({
  id: 'notifications',
  name: 'Notifications',
  routes: {
    client: [
      { path: 'notifications', handle: { title: 'Notifications' }, Component: NotificationsPage },
    ],
  },
  nav: [
    { label: 'Notifications', path: 'notifications', area: 'client' },
  ],
})
