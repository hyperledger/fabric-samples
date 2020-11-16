/*
 * SPDX-License-Identifier: Apache-2.0
 */

module.exports = {
    env: {
        node: true,
        es6: true,
        mocha: true
    },
    parserOptions: {
        ecmaVersion: 8,
        sourceType: 'script'
    },
    extends: "eslint:recommended",
    rules: {
        indent: ['error', 4],
        'linebreak-style': ['error', 'unix'],
        quotes: ['error', 'single'],
        semi: ['error', 'always'],
        'no-unused-vars': ['error', { args: 'none' }],
        'no-console': 'off',
        curly: 'error',
        eqeqeq: 'error',
        'no-throw-literal': 'error',
        strict: 'error',
        'no-var': 'error',
        'dot-notation': 'error',
        'no-tabs': 'error',
        'no-trailing-spaces': 'error',
        'no-use-before-define': 'error',
        'no-useless-call': 'error',
        'no-with': 'error',
        'operator-linebreak': 'error',
        yoda: 'error',
        'quote-props': ['error', 'as-needed'],
        'no-constant-condition': ["error", { "checkLoops": false }]
    }
};
