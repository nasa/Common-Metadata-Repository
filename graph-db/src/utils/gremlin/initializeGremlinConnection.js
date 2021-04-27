const { process, driver } = require('gremlin');

exports.initializeGremlinConnection = () => {
    const traversal = process.AnonymousTraversalSource.traversal;
    const DriverRemoteConnection = driver.DriverRemoteConnection;
    const gremlinUrl = process.env.GREMLIN_URL || 'ws://localhost:8182/gremlin';

    return traversal().withRemote(new DriverRemoteConnection(gremlinUrl));
}