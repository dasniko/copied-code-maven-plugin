def log = new File(basedir, 'build.log').text

assert log.contains('[INFO] UNCHANGED de.example.Local#LIMIT')
assert log.contains('[INFO] UNCHANGED de.example.Local#add(int, int)')
assert log.contains('[ERROR] CHANGED de.example.Local#greet(String)')
assert log.contains('upstream org.keycloak:it-fake-upstream org.keycloak.it.Foo#greet(String) 1.0 -> 2.0')
assert log.contains('-    return "Hello " + name;')
assert log.contains('+    return "Hello, " + name + "!";')
assert log.contains('[ERROR] REMOVED de.example.Local#gone()')
assert log.contains('[ERROR] UNRESOLVABLE de.example.Local#sum(int, int)')
assert log.contains('sources of org.keycloak:it-fake-upstream:0.9 not found')
assert log.contains('3 copied code element(s) need attention')

def diff = new File(basedir, 'target/copied-code/de.example.Local#greet(String).diff')
assert diff.isFile()
assert diff.text.startsWith('--- org.keycloak:it-fake-upstream:1.0')
