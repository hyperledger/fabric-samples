package commands

// ExpectedError represents a known, expected application error that should be
// displayed normally rather than treated as an unexpected failure.
type ExpectedError struct {
	Message string
}

func (e *ExpectedError) Error() string {
	return e.Message
}
