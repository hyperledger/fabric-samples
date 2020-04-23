package com.cgb.bcpinstall.biz.helper;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public class YamlRepresenter extends Representer {

    public YamlRepresenter(DumperOptions options) {
        super(options);

        this.nullRepresenter = data -> representScalar(Tag.NULL, "");
    }
}
