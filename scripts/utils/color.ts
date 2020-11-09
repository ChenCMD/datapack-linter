export const color = {
    fore: {
        reset: '\u001b[39m',
        normal: {
            black: '\u001b[30m',
            red: '\u001b[31m',
            green: '\u001b[32m',
            yellow: '\u001b[33m',
            blue: '\u001b[34m',
            magenta: '\u001b[35m',
            cyan: '\u001b[36m',
            gray: '\u001b[90m',
            white: '\u001b[97m'
        },
        light: {
            gray: '\u001b[37m',
            red: '\u001b[91m',
            green: '\u001b[92m',
            yellow: '\u001b[93m',
            blue: '\u001b[94m',
            magenta: '\u001b[95m',
            cyan: '\u001b[96m'
        }
    },
    back: {
        reset: '\u001b[49m',
        normal: {
            black: '\u001b[40m',
            red: '\u001b[41m',
            green: '\u001b[42m',
            yellow: '\u001b[43m',
            blue: '\u001b[44m',
            magenta: '\u001b[45m',
            cyan: '\u001b[46m',
            gray: '\u001b[100m',
            white: '\u001b[107m'
        },
        light: {
            gray: '\u001b[47m',
            red: '\u001b[101m',
            green: '\u001b[102m',
            yellow: '\u001b[103m',
            blue: '\u001b[104m',
            magenta: '\u001b[105m',
            cyan: '\u001b[106m'
        }
    }
} as const;

export const format = {
    resetAll: '\u001b[0m',
    bold: '\u001b[1m',
    dim: '\u001b[2m',
    underlined: '\u001b[4m',
    blink: '\u001b[5m',
    inverted: '\u001b[7m',
    hidden: '\u001b[8m',
    resetBold: '\u001b[21m',
    resetDim: '\u001b[22m',
    resetUnderlined: '\u001b[24m',
    resetBlink: '\u001b[25m',
    resetInverted: '\u001b[27m',
    resetHidden: '\u001b[28m'
} as const;