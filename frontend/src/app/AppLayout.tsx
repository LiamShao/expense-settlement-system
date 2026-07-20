import AddIcon from '@mui/icons-material/Add'
import AssignmentIcon from '@mui/icons-material/Assignment'
import FactCheckIcon from '@mui/icons-material/FactCheck'
import LogoutIcon from '@mui/icons-material/Logout'
import MenuIcon from '@mui/icons-material/Menu'
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong'
import {
  Alert,
  AppBar,
  Box,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Snackbar,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import { useState, type ReactNode } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useAuth } from '../features/auth/AuthContext'
import { canAccessAuditLogs, canAccessReviews } from '../utils/permissions'

const drawerWidth = 240

interface NavigationItem {
  label: string
  to: string
  icon: ReactNode
  visible: boolean
}

export function AppLayout() {
  const theme = useTheme()
  const narrow = useMediaQuery(theme.breakpoints.down('md'))
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [logoutDialogOpen, setLogoutDialogOpen] = useState(false)
  const [logoutPending, setLogoutPending] = useState(false)
  const [logoutError, setLogoutError] = useState<string | null>(null)
  const location = useLocation()
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  if (!user) {
    return null
  }

  const items: NavigationItem[] = [
    {
      label: '申請一覧',
      to: '/expenses',
      icon: <ReceiptLongIcon />,
      visible: true,
    },
    {
      label: '新規申請',
      to: '/expenses/new',
      icon: <AddIcon />,
      visible: true,
    },
    {
      label: '承認待ち',
      to: '/reviews',
      icon: <FactCheckIcon />,
      visible: canAccessReviews(user.role),
    },
    {
      label: '監査ログ',
      to: '/audit-logs',
      icon: <AssignmentIcon />,
      visible: canAccessAuditLogs(user.role),
    },
  ]

  const handleLogoutConfirm = async () => {
    setLogoutPending(true)
    setLogoutError(null)
    try {
      await logout()
      navigate('/login', { replace: true, state: null, flushSync: true })
    } catch (error) {
      setLogoutError(
        error instanceof Error
          ? error.message
          : 'ログアウトできませんでした。再試行してください。',
      )
    } finally {
      setLogoutPending(false)
      setLogoutDialogOpen(false)
    }
  }

  const drawer = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Toolbar sx={{ px: 2.5 }}>
        <Typography variant="h2">経費精算システム</Typography>
      </Toolbar>
      <Divider />
      <List component="nav" aria-label="メインナビゲーション" sx={{ py: 2 }}>
        {items
          .filter((item) => item.visible)
          .map((item) => (
            <ListItemButton
              component={NavLink}
              to={item.to}
              key={item.to}
              selected={
                location.pathname === item.to ||
                (item.to === '/expenses'
                  ? location.pathname.startsWith('/expenses/') &&
                    location.pathname !== '/expenses/new'
                  : location.pathname.startsWith(`${item.to}/`))
              }
              onClick={() => setDrawerOpen(false)}
              sx={{
                mx: 1,
                mb: 0.5,
                borderRadius: 1,
                borderLeft: '3px solid transparent',
                '&.Mui-selected': {
                  bgcolor: 'primary.light',
                  color: 'primary.dark',
                  borderLeftColor: 'primary.main',
                },
              }}
            >
              <ListItemIcon sx={{ minWidth: 40, color: 'inherit' }}>
                {item.icon}
              </ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
      </List>
    </Box>
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        color="inherit"
        sx={{
          zIndex: theme.zIndex.drawer + 1,
          ml: narrow ? 0 : `${drawerWidth}px`,
          width: narrow ? '100%' : `calc(100% - ${drawerWidth}px)`,
          borderBottom: 1,
          borderColor: 'divider',
          boxShadow: '0 1px 3px rgba(23, 32, 51, 0.08)',
        }}
      >
        <Toolbar sx={{ minHeight: '64px !important' }}>
          {narrow && (
            <IconButton
              edge="start"
              aria-label="メニューを開く"
              onClick={() => setDrawerOpen(true)}
              sx={{ mr: 1 }}
            >
              <MenuIcon />
            </IconButton>
          )}
          <Box sx={{ flexGrow: 1 }} />
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
            <Box sx={{ display: { xs: 'none', sm: 'block' }, textAlign: 'right' }}>
              <Typography sx={{ fontWeight: 700 }}>{user.name}</Typography>
              <Typography variant="caption" color="text.secondary">
                {user.department}
              </Typography>
            </Box>
            <Chip size="small" label={user.roleName} variant="outlined" />
            <Tooltip title="ログアウト">
              <IconButton
                aria-label="ログアウト"
                onClick={() => setLogoutDialogOpen(true)}
              >
                <LogoutIcon />
              </IconButton>
            </Tooltip>
          </Stack>
        </Toolbar>
      </AppBar>

      {narrow ? (
        <Drawer
          variant="temporary"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{ '& .MuiDrawer-paper': { width: drawerWidth } }}
        >
          {drawer}
        </Drawer>
      ) : (
        <Drawer
          variant="permanent"
          sx={{
            width: drawerWidth,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              width: drawerWidth,
              boxSizing: 'border-box',
            },
          }}
        >
          {drawer}
        </Drawer>
      )}

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          bgcolor: 'background.default',
          pt: '64px',
        }}
      >
        <Box
          sx={{
            width: '100%',
            maxWidth: 1440,
            mx: 'auto',
            p: { xs: 2, md: 3 },
          }}
        >
          <Outlet />
        </Box>
      </Box>

      <ConfirmDialog
        open={logoutDialogOpen}
        title="ログアウトしますか？"
        description="現在のセッションを終了してログイン画面に戻ります。"
        confirmLabel="ログアウトする"
        pending={logoutPending}
        onCancel={() => setLogoutDialogOpen(false)}
        onConfirm={handleLogoutConfirm}
      />
      <Snackbar
        open={Boolean(logoutError)}
        autoHideDuration={6000}
        onClose={() => setLogoutError(null)}
      >
        <Alert
          severity="error"
          variant="filled"
          onClose={() => setLogoutError(null)}
        >
          {logoutError}
        </Alert>
      </Snackbar>
    </Box>
  )
}
