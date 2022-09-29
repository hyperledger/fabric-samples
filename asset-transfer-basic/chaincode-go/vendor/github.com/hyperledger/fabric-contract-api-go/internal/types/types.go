// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package types

import (
	"fmt"
	"math"
	"reflect"
	"strconv"
	"time"

	"github.com/go-openapi/spec"
)

type basicType interface {
	Convert(string) (reflect.Value, error)
	GetSchema() *spec.Schema
}

type stringType struct{}

func (st *stringType) Convert(value string) (reflect.Value, error) {
	return reflect.ValueOf(value), nil
}

func (st *stringType) GetSchema() *spec.Schema {
	return spec.StringProperty()
}

type boolType struct{}

func (bt *boolType) Convert(value string) (reflect.Value, error) {
	var boolVal bool
	var err error
	if value != "" {
		boolVal, err = strconv.ParseBool(value)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to bool", value)
		}
	}

	return reflect.ValueOf(boolVal), nil
}

func (bt *boolType) GetSchema() *spec.Schema {
	return spec.BooleanProperty()
}

type intType struct{}

func (it *intType) Convert(value string) (reflect.Value, error) {
	var intVal int
	var err error
	if value != "" {
		intVal, err = strconv.Atoi(value)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to int", value)
		}
	}

	return reflect.ValueOf(intVal), nil
}

func (it *intType) GetSchema() *spec.Schema {
	return spec.Int64Property()
}

type int8Type struct{}

func (it *int8Type) Convert(value string) (reflect.Value, error) {
	var intVal int8
	if value != "" {
		int64val, err := strconv.ParseInt(value, 10, 8)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to int8", value)
		}

		intVal = int8(int64val)
	}

	return reflect.ValueOf(intVal), nil
}

func (it *int8Type) GetSchema() *spec.Schema {
	return spec.Int8Property()
}

type int16Type struct{}

func (it *int16Type) Convert(value string) (reflect.Value, error) {
	var intVal int16
	if value != "" {
		int64val, err := strconv.ParseInt(value, 10, 16)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to int16", value)
		}

		intVal = int16(int64val)
	}

	return reflect.ValueOf(intVal), nil
}

func (it *int16Type) GetSchema() *spec.Schema {
	return spec.Int16Property()
}

type int32Type struct{}

func (it *int32Type) Convert(value string) (reflect.Value, error) {
	var intVal int32
	if value != "" {
		int64val, err := strconv.ParseInt(value, 10, 32)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to int32", value)
		}

		intVal = int32(int64val)
	}

	return reflect.ValueOf(intVal), nil
}

func (it *int32Type) GetSchema() *spec.Schema {
	return spec.Int32Property()
}

type int64Type struct{}

func (it *int64Type) Convert(value string) (reflect.Value, error) {
	var intVal int64
	var err error
	if value != "" {
		intVal, err = strconv.ParseInt(value, 10, 64)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to int64", value)
		}
	}

	return reflect.ValueOf(intVal), nil
}

func (it *int64Type) GetSchema() *spec.Schema {
	return spec.Int64Property()
}

type uintType struct{}

func (ut *uintType) Convert(value string) (reflect.Value, error) {
	var uintVal uint
	if value != "" {
		uint64Val, err := strconv.ParseUint(value, 10, 64)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to uint", value)
		}

		uintVal = uint(uint64Val)
	}

	return reflect.ValueOf(uintVal), nil
}

func (ut *uintType) GetSchema() *spec.Schema {
	schema := spec.Float64Property()

	multOf := float64(1)
	schema.MultipleOf = &multOf
	minimum := float64(0)
	schema.Minimum = &minimum
	maximum := float64(math.MaxUint64)
	schema.Maximum = &maximum
	return schema
}

type uint8Type struct{}

func (ut *uint8Type) Convert(value string) (reflect.Value, error) {
	var uintVal uint8
	if value != "" {
		uint64Val, err := strconv.ParseUint(value, 10, 8)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to uint8", value)
		}

		uintVal = uint8(uint64Val)
	}

	return reflect.ValueOf(uintVal), nil
}

func (ut *uint8Type) GetSchema() *spec.Schema {
	schema := spec.Int32Property()
	minimum := float64(0)
	schema.Minimum = &minimum
	maximum := float64(math.MaxUint8)
	schema.Maximum = &maximum
	return schema
}

type uint16Type struct{}

func (ut *uint16Type) Convert(value string) (reflect.Value, error) {
	var uintVal uint16
	if value != "" {
		uint64Val, err := strconv.ParseUint(value, 10, 16)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to uint16", value)
		}

		uintVal = uint16(uint64Val)
	}

	return reflect.ValueOf(uintVal), nil
}

func (ut *uint16Type) GetSchema() *spec.Schema {
	schema := spec.Int64Property()
	minimum := float64(0)
	schema.Minimum = &minimum
	maximum := float64(math.MaxUint16)
	schema.Maximum = &maximum
	return schema
}

type uint32Type struct{}

func (ut *uint32Type) Convert(value string) (reflect.Value, error) {
	var uintVal uint32
	if value != "" {
		uint64Val, err := strconv.ParseUint(value, 10, 32)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to uint32", value)
		}

		uintVal = uint32(uint64Val)
	}

	return reflect.ValueOf(uintVal), nil
}

func (ut *uint32Type) GetSchema() *spec.Schema {
	schema := spec.Int64Property()
	minimum := float64(0)
	schema.Minimum = &minimum
	maximum := float64(4294967295)
	schema.Maximum = &maximum
	return schema
}

type uint64Type struct{}

func (ut *uint64Type) Convert(value string) (reflect.Value, error) {
	var uintVal uint64
	var err error
	if value != "" {
		uintVal, err = strconv.ParseUint(value, 10, 64)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to uint64", value)
		}
	}

	return reflect.ValueOf(uintVal), nil
}

func (ut *uint64Type) GetSchema() *spec.Schema {
	schema := spec.Float64Property()
	multOf := float64(1)
	schema.MultipleOf = &multOf
	minimum := float64(0)
	schema.Minimum = &minimum
	maximum := float64(18446744073709551615)
	schema.Maximum = &maximum
	return schema
}

type float32Type struct{}

func (ft *float32Type) Convert(value string) (reflect.Value, error) {
	var floatVal float32
	if value != "" {
		float64Val, err := strconv.ParseFloat(value, 32)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to float32", value)
		}

		floatVal = float32(float64Val)
	}

	return reflect.ValueOf(floatVal), nil
}

func (ft *float32Type) GetSchema() *spec.Schema {
	return spec.Float32Property()
}

type float64Type struct{}

func (ft *float64Type) Convert(value string) (reflect.Value, error) {
	var floatVal float64
	var err error
	if value != "" {
		floatVal, err = strconv.ParseFloat(value, 64)

		if err != nil {
			return reflect.Value{}, fmt.Errorf("Cannot convert passed value %s to float64", value)
		}
	}

	return reflect.ValueOf(floatVal), nil
}

func (ft *float64Type) GetSchema() *spec.Schema {
	return spec.Float64Property()
}

type interfaceType struct{}

func (st *interfaceType) Convert(value string) (reflect.Value, error) {
	return reflect.ValueOf(value), nil
}

func (st *interfaceType) GetSchema() *spec.Schema {
	return new(spec.Schema)
}

// BasicTypes the base types usable in the contract api
var BasicTypes = map[reflect.Kind]basicType{
	reflect.Bool:      new(boolType),
	reflect.Float32:   new(float32Type),
	reflect.Float64:   new(float64Type),
	reflect.Int:       new(intType),
	reflect.Int8:      new(int8Type),
	reflect.Int16:     new(int16Type),
	reflect.Int32:     new(int32Type),
	reflect.Int64:     new(int64Type),
	reflect.String:    new(stringType),
	reflect.Uint:      new(uintType),
	reflect.Uint8:     new(uint8Type),
	reflect.Uint16:    new(uint16Type),
	reflect.Uint32:    new(uint32Type),
	reflect.Uint64:    new(uint64Type),
	reflect.Interface: new(interfaceType),
}

// ErrorType reflect type for errors
var ErrorType = reflect.TypeOf((*error)(nil)).Elem()

// TimeType reflect type for time
var TimeType = reflect.TypeOf(time.Time{})
