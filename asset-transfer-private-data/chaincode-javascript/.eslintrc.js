/*
 * SPDX-License-Identifier: Apache-2.0
 */

module.exports = {
    env: {
        node: true,
        mocha: true,
        es6: true
    },
    parserOptions: {
        ecmaVersion: 8,
        sourceType: 'script'
    },
    rules: {
        indent: ['error', 4],
        'linebreak-style': ['error', 'unix'],
        'no-unused-vars': ['error', { args: 'none' }],
        'no-console': 'off',
        curly: 'error',
        'no-throw-literal': 'error',
        strict: 'error',
        'no-var': 'error',
        'dot-notation': 'error',
        'no-use-before-define': 'error',
        'no-useless-call': 'error',
        'no-with': 'error',
        'operator-linebreak': 'error',
        yoda: 'error',
        'quote-props': ['error', 'as-needed'],
        'no-constant-condition': ["error", { "checkLoops": false }]
    }
};
