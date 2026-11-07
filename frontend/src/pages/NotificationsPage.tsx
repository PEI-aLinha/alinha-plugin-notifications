import { useQuery } from '@tanstack/react-query'
import { useHost } from '@alinha/plugin-sdk'
import { PageHeader } from '@alinha/ui'

export function NotificationsPage() {
  const { api } = useHost()
  const ping = useQuery({
    queryKey: ['notifications', 'ping'],
    queryFn: () => api<{ plugin: string; status: string }>('/notifications/ping'),
  })

  return (
    <>
      <PageHeader title="Notifications" />
      <p>Backend: {ping.isPending ? 'a verificar…' : ping.isError ? 'indisponível' : ping.data.status}</p>
    </>
  )
}
