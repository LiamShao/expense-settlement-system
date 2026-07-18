import { createTheme } from '@mui/material/styles'

export const appTheme = createTheme({
  palette: {
    primary: {
      main: '#1D4ED8',
      dark: '#1E3A8A',
      light: '#DBEAFE',
    },
    secondary: {
      main: '#475569',
    },
    background: {
      default: '#F6F8FB',
      paper: '#FFFFFF',
    },
    text: {
      primary: '#172033',
      secondary: '#5F6B7A',
    },
    divider: '#D8DEE8',
    success: {
      main: '#15803D',
    },
    warning: {
      main: '#B45309',
    },
    error: {
      main: '#B42318',
    },
    info: {
      main: '#0369A1',
    },
  },
  typography: {
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Noto Sans JP", "Yu Gothic UI", "Hiragino Kaku Gothic ProN", Meiryo, sans-serif',
    fontSize: 14,
    h1: {
      fontSize: 24,
      fontWeight: 700,
      lineHeight: 1.4,
    },
    h2: {
      fontSize: 18,
      fontWeight: 700,
      lineHeight: 1.5,
    },
    button: {
      fontSize: 14,
      fontWeight: 600,
      textTransform: 'none',
    },
  },
  shape: {
    borderRadius: 8,
  },
  components: {
    MuiButton: {
      defaultProps: {
        disableElevation: true,
      },
      styleOverrides: {
        root: {
          minHeight: 40,
          borderRadius: 6,
        },
      },
    },
    MuiTextField: {
      defaultProps: {
        size: 'small',
      },
    },
    MuiFormControl: {
      defaultProps: {
        size: 'small',
      },
    },
    MuiPaper: {
      defaultProps: {
        elevation: 0,
      },
    },
    MuiTableCell: {
      styleOverrides: {
        head: {
          fontSize: 13,
          fontWeight: 700,
          backgroundColor: '#F8FAFC',
        },
      },
    },
  },
})
