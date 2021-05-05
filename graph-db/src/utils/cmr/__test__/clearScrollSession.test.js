const { clearScrollSession } = require('../clearScrollSession')
const nock = require('nock')

process.env.CMR_ROOT = 'http://localhost'
describe('clearScrollSession', () => {

    test('Blank scroll session', async () => {
        nock(/localhost/).post(/search/)
            .reply(204, {})

        const consoleOutput = jest.spyOn(console, 'warn')
        const response = await clearScrollSession()

        expect(consoleOutput).toHaveBeenCalledWith('Scroll ID was null or undefined: [undefined]')
        expect(response).toBe(null)
    })

    test('Valid scroll id', async () => {
        nock(/localhost/).post(/search/)
            .reply(204, {})

        await clearScrollSession('196827907')
            .then(res => expect(res).toEqual(204))
    })
})