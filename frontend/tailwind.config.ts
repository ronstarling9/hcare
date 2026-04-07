import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        dark: '#1a1a24',
        'dark-mid': '#2e2e38',
        blue: '#1a9afa',
        surface: '#f6f6fa',
        border: '#eaeaf2',
        'text-primary': '#1a1a24',
        'text-secondary': '#747480',
        'text-muted': '#94a3b8',
      },
      fontFamily: {
        sans: [
          '-apple-system',
          'BlinkMacSystemFont',
          "'Segoe UI'",
          'sans-serif',
        ],
      },
    },
  },
  plugins: [],
} satisfies Config
