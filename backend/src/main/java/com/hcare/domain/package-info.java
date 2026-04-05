@FilterDef(
    name = "agencyFilter",
    parameters = @ParamDef(name = "agencyId", type = UUID.class)
)
package com.hcare.domain;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import java.util.UUID;
