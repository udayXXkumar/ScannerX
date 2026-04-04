import { Outlet, useLocation } from 'react-router-dom'
import MainLayout from '../components/ui/MainLayout'
import ContentLayout from '../components/ui/ContentLayout'
import { getRouteMeta } from '../config/navigation'

const AppLayout = () => {
  const location = useLocation()
  const routeMeta = getRouteMeta(location.pathname)

  return (
    <MainLayout title={routeMeta.title} icon={routeMeta.icon}>
      <ContentLayout>
        <Outlet />
      </ContentLayout>
    </MainLayout>
  )
}

export default AppLayout
