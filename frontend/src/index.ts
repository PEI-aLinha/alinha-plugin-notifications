import { definePlugin } from '@alinha/plugin-sdk'
import { Bell } from 'lucide-react'
import { NotificationsPage } from './pages/NotificationsPage'

/**
 * The plugin's frontend entry point. The shell reads this object and mounts
 * the routes and the menu — nothing to register by hand in the shell.
 *   area 'client'     → /org/:org/<path>
 *   area 'consultant' → /portfolio/:org/<path>
 * User-visible text (title, label) is in European Portuguese, plain language
 * (UI requirement in the technical report). Paths are in English.
 */
export default definePlugin({
  id: 'notifications',
  name: 'Notifications',
  routes: {
    client: [
      { path: 'notifications', handle: { title: 'Notificações' }, Component: NotificationsPage },
    ],
  },
  nav: [
    { label: 'Notificações', path: 'notifications', area: 'client', icon: Bell, order: 90 },
  ],
})
