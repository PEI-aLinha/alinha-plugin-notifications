import { Bell } from 'lucide-react'
import { EmptyState, PageHeader } from '@alinha/ui'

export function NotificationsPage() {
  return (
    <>
      <PageHeader title="Notificações" subtitle="Avisos e lembretes sobre a sua organização." />
      <EmptyState icon={<Bell className="size-5" aria-hidden />} title="Em construção">
        Aqui vai ver os avisos importantes, como prazos a aproximar-se, novas recomendações ou alterações feitas pela equipa de consultoria.
      </EmptyState>
    </>
  )
}
