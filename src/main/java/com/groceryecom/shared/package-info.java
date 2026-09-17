/**
 * Shared kernel: value types (such as Money), base persistence types, the API
 * response envelope and base exceptions. Open to every module; must not depend
 * on any module.
 */
@ApplicationModule(displayName = "Shared kernel", type = ApplicationModule.Type.OPEN)
package com.groceryecom.shared;

import org.springframework.modulith.ApplicationModule;
