import { render, screen } from '@testing-library/react'
import App from './App'

describe('App', () => {
  it('Phase 14A の frontend foundation を表示する', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', { name: '経費精算システム' }),
    ).toBeInTheDocument()
    expect(screen.getByText(/React \+ MUI frontend foundation/)).toBeVisible()
  })
})
