import { definePlugin } from '@alinha/plugin-sdk'
import { NotificationsPage } from './pages/NotificationsPage'

/**
 * The plugin's frontend entry point. The shell reads this object and mounts
 * the routes and the menu — nothing to register by hand in the shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /carteira/<path>
 * User-visible text (title, label) and paths are in Portuguese, plain language
 * (UI requirement in the technical report).
 */
export default definePlugin({
  id: 'notifications',
  name: 'Notifications',
  routes: {
    client: [
      { path: 'notificacoes', handle: { title: 'Notificações' }, Component: NotificationsPage },
    ],
  },
  nav: [
    { label: 'Notificações', path: 'notificacoes', area: 'client' },
  ],
})
