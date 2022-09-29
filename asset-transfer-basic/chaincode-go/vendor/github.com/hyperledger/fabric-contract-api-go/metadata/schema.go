// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package metadata

import (
	"fmt"
	"reflect"
	"strings"
	"unicode"

	"github.com/go-openapi/spec"
	"github.com/hyperledger/fabric-contract-api-go/internal/types"
)

// GetSchema returns the open api spec schema for a given type. For struct types the property
// name used in the generated schema will be the name of the property unless a metadata or json
// tag exists for the property. Metadata tags take precedence over json tags. Private properties
// without a metadata tag will be ignored. Json tags are not used for private properties. Components
// will be added to component metadata if the field is a struct type. The schema will then reference
// this component
func GetSchema(field reflect.Type, components *ComponentMetadata) (*spec.Schema, error) {
	return getSchema(field, components, false)
}

func getSchema(field reflect.Type, components *ComponentMetadata, nested bool) (*spec.Schema, error) {
	var schema *spec.Schema
	var err error

	if bt, ok := types.BasicTypes[field.Kind()]; !ok {
		if field == types.TimeType {
			schema = spec.DateTimeProperty()
		} else if field.Kind() == reflect.Array {
			schema, err = buildArraySchema(reflect.New(field).Elem(), components, nested)
		} else if field.Kind() == reflect.Slice {
			schema, err = buildSliceSchema(reflect.MakeSlice(field, 1, 1), components, nested)
		} else if field.Kind() == reflect.Map {
			schema, err = buildMapSchema(reflect.MakeMap(field), components, nested)
		} else if field.Kind() == reflect.Struct || (field.Kind() == reflect.Ptr && field.Elem().Kind() == reflect.Struct) {
			schema, err = buildStructSchema(field, components, nested)
		} else {
			return nil, fmt.Errorf("%s was not a valid type", field.String())
		}
	} else {
		return bt.GetSchema(), nil
	}

	if err != nil {
		return nil, err
	}

	return schema, nil
}

func buildArraySchema(array reflect.Value, components *ComponentMetadata, nested bool) (*spec.Schema, error) {
	if array.Len() < 1 {
		return nil, fmt.Errorf("Arrays must have length greater than 0")
	}

	lowerSchema, err := getSchema(array.Index(0).Type(), components, nested)

	if err != nil {
		return nil, err
	}

	return spec.ArrayProperty(lowerSchema), nil
}

func buildSliceSchema(slice reflect.Value, components *ComponentMetadata, nested bool) (*spec.Schema, error) {
	if slice.Len() < 1 {
		slice = reflect.MakeSlice(slice.Type(), 1, 10)
	}

	lowerSchema, err := getSchema(slice.Index(0).Type(), components, nested)

	if err != nil {
		return nil, err
	}

	return spec.ArrayProperty(lowerSchema), nil
}

func buildMapSchema(rmap reflect.Value, components *ComponentMetadata, nested bool) (*spec.Schema, error) {
	lowerSchema, err := getSchema(rmap.Type().Elem(), components, nested)

	if err != nil {
		return nil, err
	}

	return spec.MapProperty(lowerSchema), nil
}

func addComponentIfNotExists(obj reflect.Type, components *ComponentMetadata) error {
	if obj.Kind() == reflect.Ptr {
		obj = obj.Elem()
	}

	if _, ok := components.Schemas[obj.Name()]; ok {
		return nil
	}

	schema := ObjectMetadata{}
	schema.ID = obj.Name()
	schema.Required = []string{}
	schema.Properties = make(map[string]spec.Schema)
	schema.AdditionalProperties = false

	if components.Schemas == nil {
		components.Schemas = make(map[string]ObjectMetadata)
	}

	components.Schemas[obj.Name()] = schema // lock up slot for cyclic

	for i := 0; i < obj.NumField(); i++ {
		err := getField(obj.Field(i), &schema, components)

		if err != nil {
			delete(components.Schemas, obj.Name())
			return err
		}
	}

	components.Schemas[obj.Name()] = schema // include changes

	return nil
}

func getField(field reflect.StructField, schema *ObjectMetadata, components *ComponentMetadata) error {
	if field.Anonymous {
		if field.Type.Kind() == reflect.Struct {
			for i := 0; i < field.Type.NumField(); i++ {
				err := getField(field.Type.Field(i), schema, components)

				if err != nil {
					return err
				}
			}
		}

		return nil
	}

	name := field.Tag.Get("metadata")
	required := true

	if strings.Contains(name, ",") {
		spl := strings.Split(name, ",")

		name = spl[0]

		for _, val := range spl[1:] {
			if strings.TrimSpace(val) == "optional" {
				required = false
			}
		}
	}

	if (unicode.IsLower([]rune(field.Name)[0]) && name == "") || name == "-" {
		return nil
	} else if name == "" {
		name = field.Tag.Get("json")
	}

	if name == "" || name == "-" {
		name = field.Name
	}

	var err error

	propSchema, err := getSchema(field.Type, components, true)

	if err != nil {
		return err
	}

	if required {
		schema.Required = append(schema.Required, name)
	}

	schema.Properties[name] = *propSchema

	return nil
}

func buildStructSchema(obj reflect.Type, components *ComponentMetadata, nested bool) (*spec.Schema, error) {
	if obj.Kind() == reflect.Ptr {
		obj = obj.Elem()
	}

	err := addComponentIfNotExists(obj, components)

	if err != nil {
		return nil, err
	}

	refPath := "#/components/schemas/"

	if nested {
		refPath = ""
	}

	return spec.RefSchema(refPath + obj.Name()), nil
}
