package com.sequenceiq.cloudbreak.converter.mapper;

import com.sequenceiq.cloudbreak.api.model.imagecatalog.ManagementPackEntry;
import com.sequenceiq.cloudbreak.cloud.model.component.ManagementPackComponent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor"
)
@Component
public class ManagementPackComponentListMapMapperImpl implements ManagementPackComponentListMapMapper {

    @Autowired
    private ManagementPackComponentListMapper managementPackComponentListMapper;

    @Override
    public Map<String, List<ManagementPackEntry>> mapManagementPackComponentMap(Map<String, List<ManagementPackComponent>> mpacks) {
        if ( mpacks == null ) {
            return null;
        }

        Map<String, List<ManagementPackEntry>> map = new HashMap<String, List<ManagementPackEntry>>( Math.max( (int) ( mpacks.size() / .75f ) + 1, 16 ) );

        for ( java.util.Map.Entry<String, List<ManagementPackComponent>> entry : mpacks.entrySet() ) {
            String key = entry.getKey();
            List<ManagementPackEntry> value = managementPackComponentListMapper.mapManagementPackComponentMap( entry.getValue() );
            map.put( key, value );
        }

        return map;
    }
}
