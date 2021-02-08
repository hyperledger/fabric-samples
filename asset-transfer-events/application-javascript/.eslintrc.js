/*
 * SPDX-License-Identifier: Apache-2.0
 */
'use strict';

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
	extends: 'eslint:recommended',
	rules: {
		indent: ['error', 'tab'],
		'linebreak-style': ['error', 'unix'],
		quotes: ['error', 'single'],
		semi: ['error', 'always'],
		'no-unused-vars': ['error', { args: 'none' }],
		'no-console': 'off',
		curly: 'error',
		eqeqeq: 'error',
		'no-throw-literal': 'error',
		'no-var': 'error',
		'dot-notation': 'error',
		'no-trailing-spaces': 'error',
		'no-use-before-define': 'error',
		'no-useless-call': 'error',
		'no-with': 'error',
		'operator-linebreak': 'error',
		yoda: 'error',
		'quote-props': ['error', 'as-needed']
	}
};
