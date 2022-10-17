package main

import (
	"github.com/go-playground/validator/v10"
	"github.com/gofiber/fiber/v2"
)

/*
type Dog struct {
    Name      string `json:"name" validate:"required,min=3,max=12"`
    Age       int    `json:"age" validate:"required,numeric"`
    IsGoodBoy bool   `json:"isGoodBoy" validate:"required"`
}
*/

type Dog struct {
	DocType          string      `json:"docType" validate:"required,min=3,max=26"`
	Id               string      `json:"id" validate:"required,alphanum"`
	Title            string      `json:"title" validate:"required,min=3"`
	Description      string      `json:"description" validate:"required,min=3"`
	Type             string      `json:"Type" validate:"required,min=1"`
	DOI              string      `json:"DOI" validate:"required,url"`
	Url              string      `json:"url" validate:"required,url"`
	Manifest         *[]Manifest `json:"manifest" validate:"required,dive"`
	Footprint        string      `json:"footprint"`
	Keywords         []string    `json:"keywords"`
	OtherDataIdName  string      `json:"otherDataIdName"`
	OtherDataIdValue string      `json:"otherDataIdValue"`
	FundingAgencies  []string    `json:"fundingAgencies"`
	Acknowledgment   string      `json:"acknowledgment"`
	NoteForChange    string      `json:"noteForChange"`
	Contributor      string      `json:"contributor"`
	Contributor_id   string      `json:"contributor_id"`
}

// Do we need to creat a struct for Manifest, or any other field for that matter?

type Manifest struct {
	Algorithm string `json:"algorithm" validate:"required,min=3,max=26"`
	Filename  string `json:"fileName" validate:"required,alphanum"`
	Hash      string `json:"hash" validate:"required,min=3"`
}

type IError struct {
	Field string
	Tag   string
	Value string
}

var Validator = validator.New()

func ValidateAddDog(c *fiber.Ctx) error {
	var errors []*IError
	body := new(Dog)
	c.BodyParser(&body)

	err := Validator.Struct(body)
	if err != nil {
		for _, err := range err.(validator.ValidationErrors) {
			var el IError
			el.Field = err.Field()
			el.Tag = err.Tag()
			el.Value = err.Param()
			errors = append(errors, &el)
		}
		return c.Status(fiber.StatusBadRequest).JSON(errors)
	}
	return c.Next()
}

func main() {
	app := fiber.New()

	app.Get("/", func(c *fiber.Ctx) error {
		return c.SendString("Thank God it works 🙏")
	})

	app.Post("/", ValidateAddDog, func(c *fiber.Ctx) error {
		body := new(Dog)
		c.BodyParser(&body)
		return c.Status(fiber.StatusOK).JSON(body)
	})

	app.Listen(":3030")

}
