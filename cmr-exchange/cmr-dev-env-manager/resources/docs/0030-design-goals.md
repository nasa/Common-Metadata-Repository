#  • Project Design Goals

 * Use established, accepted ecosystem libs as deps (e.g., Component)
 * Enable managing each CMR service/component individually (starting, stopping, reloading)
   * include the reloading or rebuilding (as necessary) as part of the capabilities
 * Keep REPL refresh, code reloading, and service restarting separated
   * provide functions/options that allow for useful combinations of these
 * Allow for a complete system reset
   * would not touch `git`
   * as such, a rebuild and re-start of services would include any changes made on the file system
   * would lose all volatile state, REPL-only modifications
   * should be immune to errors with namespace reloading (i.e., always available)
 * Don't block the main REPL thread during component start/stop/restart
 * Only load the minimal set of needed projects/deps/namespaces;
   don't load the entire CMR code ase
   * maximize use of services
   * when reloading code in the REPL, only the loaded code will be reloaded, not the entire CMR
     code base
 * Don't require calling functions like `(reset)` before running tests
   * instead, simply require that the system be started
   * iow, name things well/sensibly
 * Provide current system state in a state-tracking atom
   * useful for the curious/debugging developer
   * more importantly, useful as feedback after developers execute commands/functions that affect
     system status (e.g., start/stop/restart)
   * include state of libs / app sources: `:unmodified` / `:modified`
 * Logging
   * easily enable/disable/set level for logging
   * support ANSI colored logging for easier reading by developers
