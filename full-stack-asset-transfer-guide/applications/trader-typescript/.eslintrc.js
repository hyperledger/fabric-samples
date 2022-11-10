module.exports = {
    env: {
        node: true,
        es2021: true,
    },
    extends: [
        'eslint:recommended',
    ],
    root: true,
    ignorePatterns: [
        'dist/',
    ],
    rules: {
        'arrow-spacing': ['error'],
        'comma-style': ['error'],
        complexity: ['error', 10],
        'eol-last': ['error'],
        'generator-star-spacing': ['error', 'after'],
        'key-spacing': [
            'error',
            {
                beforeColon: false,
                afterColon: true,
                mode: 'minimum',
            },
        ],
        'keyword-spacing': ['error'],
        'no-multiple-empty-lines': ['error'],
        'no-trailing-spaces': ['error'],
        'no-whitespace-before-property': ['error'],
        'object-curly-newline': ['error'],
        'padded-blocks': ['error', 'never'],
        'rest-spread-spacing': ['error'],
        'semi-style': ['error'],
        'space-before-blocks': ['error'],
        'space-in-parens': ['error'],
        'space-unary-ops': ['error'],
        'spaced-comment': ['error'],
        'template-curly-spacing': ['error'],
        'yield-star-spacing': ['error', 'after'],
    },
    overrides: [
        {
            files: [
                '**/*.ts',
            ],
            parser: '@typescript-eslint/parser',
            parserOptions: {
                sourceType: 'module',
                ecmaFeatures: {
                    impliedStrict: true,
                },
                project: './tsconfig.json',
                tsconfigRootDir: process.env.TSCONFIG_ROOT_DIR || __dirname,
            },
            plugins: [
                '@typescript-eslint',
            ],
            extends: [
                'eslint:recommended',
                'plugin:@typescript-eslint/recommended',
                'plugin:@typescript-eslint/recommended-requiring-type-checking',
            ],
            rules: {
                '@typescript-eslint/comma-spacing': ['error'],
                '@typescript-eslint/explicit-function-return-type': [
                    'error',
                    {
                        allowExpressions: true,
                    },
                ],
                '@typescript-eslint/func-call-spacing': ['error'],
                '@typescript-eslint/member-delimiter-style': ['error'],
                '@typescript-eslint/indent': [
                    'error',
                    4,
                    {
                        SwitchCase: 0,
                    },
                ],
                '@typescript-eslint/prefer-nullish-coalescing': ['error'],
                '@typescript-eslint/prefer-optional-chain': ['error'],
                '@typescript-eslint/prefer-reduce-type-parameter': ['error'],
                '@typescript-eslint/prefer-return-this-type': ['error'],
                '@typescript-eslint/quotes': ['error', 'single'],
                '@typescript-eslint/type-annotation-spacing': ['error'],
                '@typescript-eslint/semi': ['error'],
                '@typescript-eslint/space-before-function-paren': [
                    'error',
                    {
                        anonymous: 'never',
                        named: 'never',
                        asyncArrow: 'always',
                    },
                ],
            },
        },
    ],
};
