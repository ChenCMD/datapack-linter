export const color = {
    fore: {
        reset: '\e[39m',
        normal: {
            black: '\e[30m',
            red: '\e[31m',
            green: '\e[32m',
            yellow: '\e[33m',
            blue: '\e[34m',
            magenta: '\e[35m',
            cyan: '\e[36m',
            gray: '\e[90m',
            white: '\e[97m'
        },
        light: {
            gray: '\e[37m',
            red: '\e[91m',
            green: '\e[92m',
            yellow: '\e[93m',
            blue: '\e[94m',
            magenta: '\e[95m',
            cyan: '\e[96m'
        }
    },
    back: {
        reset: '\e[49m',
        normal: {
            black: '\e[40m',
            red: '\e[41m',
            green: '\e[42m',
            yellow: '\e[43m',
            blue: '\e[44m',
            magenta: '\e[45m',
            cyan: '\e[46m',
            gray: '\e[100m',
            white: '\e[107m'
        },
        light: {
            gray: '\e[47m',
            red: '\e[101m',
            green: '\e[102m',
            yellow: '\e[103m',
            blue: '\e[104m',
            magenta: '\e[105m',
            cyan: '\e[106m'
        }
    }
} as const;

export const format = {
    resetAll: '\e[0m',
    bold: '\e[1m',
    dim: '\e[2m',
    underlined: '\e[4m',
    blink: '\e[5m',
    inverted: '\e[7m',
    hidden: '\e[8m',
    resetBold: '\e[21m',
    resetDim: '\e[22m',
    resetUnderlined: '\e[24m',
    resetBlink: '\e[25m',
    resetInverted: '\e[27m',
    resetHidden: '\e[28m'
} as const;