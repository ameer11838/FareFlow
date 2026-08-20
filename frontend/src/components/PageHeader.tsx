export function PageHeader({ eyebrow, title, subtitle, actions }: {
  eyebrow?: string
  title: string
  subtitle?: string
  actions?: React.ReactNode
}) {
  return (
    <header className="page-header">
      <div className="page-header-row">
        <div>
          {eyebrow && <p className="page-eyebrow">{eyebrow}</p>}
          <h1 className="page-title">{title}</h1>
          {subtitle && <p className="page-subtitle">{subtitle}</p>}
        </div>
        {actions}
      </div>
    </header>
  )
}
