package porcupine

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"regexp"
	"strconv"
	"testing"
)

type etcdInput struct {
	op   uint8 // 0 => read, 1 => write, 2 => cas
	arg1 int   // used for write, or for CAS from argument
	arg2 int   // used for CAS to argument
}

type etcdOutput struct {
	ok      bool // used for CAS
	exists  bool // used for read
	value   int  // used for read
	unknown bool // used when operation times out
}

var etcdModel = Model{
	Init: func() interface{} { return -1000000 }, // -1000000 corresponds with nil
	Step: func(state interface{}, input interface{}, output interface{}) (bool, interface{}) {
		st := state.(int)
		inp := input.(etcdInput)
		out := output.(etcdOutput)
		if inp.op == 0 {
			// read
			ok := (out.exists == false && st == -1000000) || (out.exists == true && st == out.value) || out.unknown
			return ok, state
		} else if inp.op == 1 {
			// write
			return true, inp.arg1
		} else {
			// cas
			ok := (inp.arg1 == st && out.ok) || (inp.arg1 != st && !out.ok) || out.unknown
			result := st
			if inp.arg1 == st {
				result = inp.arg2
			}
			return ok, result
		}
	},
	DescribeOperation: func(input, output interface{}) string {
		inp := input.(etcdInput)
		out := output.(etcdOutput)
		switch inp.op {
		case 0:
			var read string
			if out.exists {
				read = fmt.Sprintf("%d", out.value)
			} else {
				read = "null"
			}
			return fmt.Sprintf("read() -> %s", read)
		case 1:
			return fmt.Sprintf("write(%d)", inp.arg1)
		case 2:
			var ret string
			if out.unknown {
				ret = "unknown"
			} else if out.ok {
				ret = "ok"
			} else {
				ret = "fail"
			}
			return fmt.Sprintf("cas(%d, %d) -> %s", inp.arg1, inp.arg2, ret)

		default:
			return "<invalid>"
		}
	},
}

func parseCASJepsenLog(filename string) []Event {
	file, err := os.Open(filename)
	if err != nil {
		panic("can't open file")
	}
	defer file.Close()

	reader := bufio.NewReader(file)

	invokeWrite, _ := regexp.Compile(`{:type :invoke, :f :write, :value (\d+), :time \d+, :process (\d+), :index \d+}`)
	invokeCas, _ := regexp.Compile(`{:type :invoke, :f :cas, :value \[(\d+) (\d+)\], :time \d+, :process (\d+), :index \d+}`)
	returnWrite, _ := regexp.Compile(`{:type :ok, :f :write, :value \d+, :time \d+, :process (\d+), :index \d+}`)
	returnCas, _ := regexp.Compile(`{:type :(ok|fail), :f :cas, :value \[\d+ \d+\], :time \d+, :process (\d+), :index \d+}`)

	var events []Event = nil

	id := 0
	procIdMap := make(map[int]int)
	for {
		lineBytes, isPrefix, err := reader.ReadLine()
		if err == io.EOF {
			break
		} else if err != nil {
			panic("error while reading file: " + err.Error())
		}
		if isPrefix {
			panic("can't handle isPrefix")
		}
		line := string(lineBytes)

		switch {
		case invokeWrite.MatchString(line):
			args := invokeWrite.FindStringSubmatch(line)
			proc, _ := strconv.Atoi(args[2])
			value, _ := strconv.Atoi(args[1])
			events = append(events, Event{proc, CallEvent, etcdInput{op: 1, arg1: value}, id})
			procIdMap[proc] = id
			id++
		case invokeCas.MatchString(line):
			args := invokeCas.FindStringSubmatch(line)
			proc, _ := strconv.Atoi(args[3])
			from, _ := strconv.Atoi(args[1])
			to, _ := strconv.Atoi(args[2])
			events = append(events, Event{proc, CallEvent, etcdInput{op: 2, arg1: from, arg2: to}, id})
			procIdMap[proc] = id
			id++
		case returnWrite.MatchString(line):
			args := returnWrite.FindStringSubmatch(line)
			proc, _ := strconv.Atoi(args[1])
			matchId := procIdMap[proc]
			delete(procIdMap, proc)
			events = append(events, Event{proc, ReturnEvent, etcdOutput{}, matchId})
		case returnCas.MatchString(line):
			args := returnCas.FindStringSubmatch(line)
			proc, _ := strconv.Atoi(args[2])
			matchId := procIdMap[proc]
			delete(procIdMap, proc)
			events = append(events, Event{proc, ReturnEvent, etcdOutput{ok: args[1] == "ok"}, matchId})
		}
	}

	for proc, matchId := range procIdMap {
		events = append(events, Event{proc, ReturnEvent, etcdOutput{unknown: true}, matchId})
	}

	return events
}

func CheckCASJepsenFile(filename string) CheckResult {
	events := parseCASJepsenLog(filename)
	res, _ := CheckEventsVerbose(etcdModel, events, 0)
	// file, err := ioutil.TempFile("", "*.html")
	// if err != nil {
	// 	t.Fatal("Error creating temp file")
	// }
	// err = Visualize(etcdModel, info, file)
	// if err != nil {
	// 	t.Fatalf("visualization failed")
	// }
	// t.Logf("wrote visualization to %s", file.Name())
	// for _, event := range events {
	// 	fmt.Println(event)
	// }
	return res
}

func CheckCASJepsen(t *testing.T, logNum int, correct bool) {
	events := parseCASJepsenLog(fmt.Sprintf("test_data/jepsen/cas_%03d.log", logNum))
	fmt.Print(len(events))
	res, _ := CheckEventsVerbose(etcdModel, events, 0)
	// file, err := ioutil.TempFile("", "*.html")
	// if err != nil {
	// 	t.Fatal("Error creating temp file")
	// }
	// err = Visualize(etcdModel, info, file)
	// if err != nil {
	// 	t.Fatalf("visualization failed")
	// }
	// t.Logf("wrote visualization to %s", file.Name())
	// for _, event := range events {
	// 	fmt.Println(event)
	// }
	if (res == Ok) != correct {
		t.Fatalf("expected output %v, got output %v", correct, res)
	}
}
