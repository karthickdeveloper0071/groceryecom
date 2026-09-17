/**
 * Technical platform: security, web error handling, caching and messaging
 * configuration. Open to every module; must not depend on any business module.
 */
@ApplicationModule(displayName = "Platform", type = ApplicationModule.Type.OPEN)
package com.groceryecom.platform;

import org.springframework.modulith.ApplicationModule;
