import { Box, CssBaseline, Paper, Typography } from '@mui/material'

export default function App() {
  return (
    <>
      <CssBaseline />
      <Box
        component="main"
        sx={{
          minHeight: '100vh',
          bgcolor: 'grey.100',
          p: 3,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Paper variant="outlined" sx={{ maxWidth: 560, p: 4 }}>
          <Typography component="h1" variant="h4" gutterBottom>
            経費精算システム
          </Typography>
          <Typography color="text.secondary">
            Phase 14A の React + MUI frontend foundation が利用可能です。
          </Typography>
        </Paper>
      </Box>
    </>
  )
}
