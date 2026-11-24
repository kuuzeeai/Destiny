import sys, pathlib
p = pathlib.Path("feature/drawer/src/main/kotlin/ai/opencode/feature/drawer/HomeViewDrawer.kt")
OLD = '''            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { controlsHeight = it.height / density.density },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawerNewSessionButton(enabled = !disconnected, onClick = viewState::startNewSessionFromDrawer)
                Spacer(Modifier.weight(1f))
                DrawerFooterTrailing(store, disconnected, reducedMotion, onOpenSettings = viewState::openSettings)
            }
'''
NEW = '''            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { controlsHeight = it.height / density.density },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawerNewSessionButton(enabled = !disconnected, onClick = viewState::startNewSessionFromDrawer)
                    Spacer(Modifier.weight(1f))
                    DrawerFooterTrailing(store, disconnected, reducedMotion, onOpenSettings = viewState::openSettings)
                }
            }
'''
if not p.exists(): sys.exit("NO EXISTE")
t = p.read_text()
if t.count(OLD) != 1: sys.exit(f"ABORTO: {t.count(OLD)} coincidencias")
t = t.replace(OLD, NEW)
if t.count("{") != t.count("}") or t.count("(") != t.count(")"): sys.exit("ABORTO: balance roto")
p.write_text(t)
print("OK f9")
