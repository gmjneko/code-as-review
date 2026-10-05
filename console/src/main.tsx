import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { App } from '@/app/App'
import { createQueryClient } from '@/app/query-client'
import { createAppRouter } from '@/app/router'
import { setupAuth } from '@/app/setup-auth'

// main.tsx is the composition root: the only module allowed to run side effects on import
// (package.json declares the rest of src side-effect free so unused exports can be dropped).
dayjs.locale('zh-cn')

const queryClient = createQueryClient()
const router = createAppRouter(queryClient)
setupAuth(queryClient, router)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App queryClient={queryClient} router={router} />
  </StrictMode>,
)
