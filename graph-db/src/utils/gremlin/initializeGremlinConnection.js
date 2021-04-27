const { process: p, driver } = require('gremlin');

exports.initializeGremlinConnection = () => {
    const traversal = p.AnonymousTraversalSource.traversal;
    const DriverRemoteConnection = driver.DriverRemoteConnection;
    const gremlinUrl = process.env.GREMLIN_URL;

    return traversal().withRemote(new DriverRemoteConnection(gremlinUrl));
}