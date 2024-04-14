package main

import (
	"fmt"
	"os"
	"strconv"
	"time"

	"github.com/anishathalye/porcupine/porcupine"
)

func main() {
	filename := os.Args[1]
	start := time.Now()
	porcupine.CheckCASJepsenFile(filename)
	elapsed := time.Since(start)
	fmt.Println(filename + "|" + strconv.FormatInt(elapsed.Microseconds(), 10))
}
