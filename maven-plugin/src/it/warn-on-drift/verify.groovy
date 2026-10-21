def log = new File(basedir, 'build.log').text

assert log.contains('[WARNING] CHANGED de.example.Local#greet(String)')
assert log.contains('[WARNING] REMOVED de.example.Local#gone()')
assert log.contains('[WARNING] UNRESOLVABLE de.example.Local#sum(int, int)')
assert log.contains('BUILD SUCCESS')
assert new File(basedir, 'target/copied-code/de.example.Local#greet(String).diff').isFile()
