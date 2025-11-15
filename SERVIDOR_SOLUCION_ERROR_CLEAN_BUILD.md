# 🔧 SOLUCIÓN: Error al hacer Clean and Build en EncomiendasAPI

## ❌ PROBLEMA IDENTIFICADO

```
Failed to delete C:\Users\ASUS\Documents\www\EncomiendasAPI\target\EncomiendasAPI\WEB-INF\lib\osgi-resource-locator-1.0.1.jar
```

**Causa:** GlassFish tiene bloqueados los archivos JAR en la carpeta `target` porque la aplicación está desplegada y en ejecución.

---

## ✅ SOLUCIÓN RÁPIDA (3 PASOS)

### PASO 1: Detener GlassFish

**Opción A - Desde NetBeans:**
1. Ve a la pestaña **Services** (Servicios)
2. Expande **Servers** (Servidores)
3. Clic derecho en **GlassFish Server**
4. Selecciona **Stop** (Detener)

**Opción B - Desde Línea de Comandos:**
```cmd
cd C:\glassfish4\bin
asadmin stop-domain domain1
```

**Opción C - Administrador de Tareas:**
1. `Ctrl + Shift + Esc`
2. Busca el proceso **java.exe** relacionado con GlassFish
3. Finalizar tarea

---

### PASO 2: Eliminar la carpeta target manualmente

```cmd
cd C:\Users\ASUS\Documents\www\EncomiendasAPI
rmdir /s /q target
```

Si da error de "archivo en uso", cierra NetBeans y vuelve a intentar.

---

### PASO 3: Hacer Clean and Build

Ahora sí ejecuta:
```cmd
cd C:\Users\ASUS\Documents\www\EncomiendasAPI
mvn clean install
```

O desde NetBeans: **Clic derecho en el proyecto → Clean and Build**

---

## 🔄 FLUJO RECOMENDADO PARA DESARROLLAR

Para evitar este problema en el futuro:

### 1️⃣ Undeploy (Desplegar aplicación anterior)
```
NetBeans → Services → Servers → GlassFish Server → Applications 
→ Clic derecho en EncomiendasAPI → Undeploy
```

### 2️⃣ Clean and Build
```
Clic derecho en proyecto → Clean and Build
```

### 3️⃣ Run/Deploy
```
Clic derecho en proyecto → Run
```

---

## 🛠️ SOLUCIÓN ALTERNATIVA: Configurar Maven para ignorar errores de limpieza

Si el problema persiste, agrega esto a tu `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-clean-plugin</artifactId>
            <version>3.2.0</version>
            <configuration>
                <failOnError>false</failOnError>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## ⚠️ ADVERTENCIA ADICIONAL

También vi este warning:
```
The POM for unknown.binary:javax.persistence:jar:SNAPSHOT is missing
```

Esto puede causar problemas. Asegúrate de tener en tu `pom.xml`:

```xml
<dependency>
    <groupId>javax</groupId>
    <artifactId>javaee-api</artifactId>
    <version>7.0</version>
    <scope>provided</scope>
</dependency>
```

**NO uses:**
```xml
<!-- ❌ INCORRECTO -->
<dependency>
    <groupId>unknown.binary</groupId>
    <artifactId>javax.persistence</artifactId>
    ...
</dependency>
```

---

## 📋 CHECKLIST DE VERIFICACIÓN

Después de hacer clean and build exitosamente:

- [ ] GlassFish detenido
- [ ] Carpeta `target` eliminada
- [ ] `mvn clean install` ejecutado sin errores
- [ ] WAR generado en `target/EncomiendasAPI.war`
- [ ] Verificar que el WAR contiene:
  - `WEB-INF/classes/com/encomiendas/model/Recolector.class`
  - `WEB-INF/classes/com/encomiendas/service/RecolectorService.class`
  - `WEB-INF/classes/com/encomiendas/resource/RecolectorResource.class`
  - `WEB-INF/classes/META-INF/persistence.xml`

Para verificar el contenido del WAR:
```cmd
cd C:\Users\ASUS\Documents\www\EncomiendasAPI\target
jar -tf EncomiendasAPI.war | findstr Recolector
```

---

## 🚀 DESPUÉS DE SOLUCIONAR

1. Inicia GlassFish
2. Redesplega EncomiendasAPI
3. Verifica los logs de inicialización
4. Prueba el endpoint: `http://localhost:8080/EncomiendasAPI/api/recolectores`

---

## 💡 TIPS PARA EVITAR ESTE PROBLEMA

1. **Siempre detén GlassFish antes de Clean and Build**
2. **Usa "Undeploy" antes de recompilar**
3. **Cierra NetBeans si persiste el bloqueo de archivos**
4. **Configura `failOnError=false` en maven-clean-plugin** (como se mostró arriba)

