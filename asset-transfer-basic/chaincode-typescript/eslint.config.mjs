import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(js.configs.recommended, ...tseslint.configs.strictTypeChecked, {
    languageOptions: {
        ecmaVersion: 2023,
        sourceType: 'module',
        parserOptions: {
            project: 'tsconfig.json',
            tsconfigRootDir: import.meta.dirname,
        },
    },
});
