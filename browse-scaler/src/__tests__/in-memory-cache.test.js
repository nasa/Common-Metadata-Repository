const { getValue, setValue } = require('../in-memory-cache')

describe ('testing in memory token cache', () => {

    test ('getting value before token is set', () => {
        expect(getValue('my token')).toBeFalsy()
    });

    test ('round trip', () => {
        setValue('anything', 'token')
        expect(getValue('anything')).toBe('token')
    });
});
