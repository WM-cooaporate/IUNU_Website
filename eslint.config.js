import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    // k6 scripts are not browser code. They run in k6's own runtime, which
    // supplies __ENV and __VU as globals and resolves "k6/http" itself, so the
    // browser config below reports both as errors. Linting them properly would
    // mean a second config block for a runtime this project does not otherwise
    // know about; k6 checks its own scripts when it runs them.
    files: ['load-tests/**/*.js'],
    rules: { 'no-undef': 'off' },
  },
  {
    files: ['**/*.{js,jsx}'],
    ignores: ['load-tests/**/*.js'],
    extends: [
      js.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
  },
])
