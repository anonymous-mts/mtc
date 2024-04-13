package main

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"regexp"
	"strconv"
	"time"
)

type OperationType int
type StatusType bool
type ResultType int

const (
	write = 0
	cas   = 1
)

const (
	fail = false
	ok   = true
)

const (
	repeatedWrite      = 0
	repeatedRead       = 1
	incompleteCASChain = 2
	invalidTime        = 3
	valid              = 4
)

type TimedEvent struct {
	ClientId  int
	StartTime int64
	EndTime   int64
	Status    StatusType
	Operation Operation
}

type Operation struct {
	OperationType OperationType
	Payload       interface{}
}

func parseCASJepsenLog(filename string) []*TimedEvent {
	file, err := os.Open(filename)
	if err != nil {
		panic("can't open file")
	}
	defer file.Close()

	reader := bufio.NewReader(file)

	invokeWrite, _ := regexp.Compile(`{:type :invoke, :f :write, :value (\d+), :time (\d+), :process (\d+), :index \d+}`)
	invokeCas, _ := regexp.Compile(`{:type :invoke, :f :cas, :value \[(\d+) (\d+)\], :time (\d+), :process (\d+), :index \d+}`)
	returnWrite, _ := regexp.Compile(`{:type :ok, :f :write, :value \d+, :time (\d+), :process (\d+), :index \d+}`)
	returnCas, _ := regexp.Compile(`{:type :(ok|fail), :f :cas, :value \[\d+ \d+\], :time (\d+), :process (\d+), :index \d+}`)

	var events []*TimedEvent = nil

	procIdMap := make(map[int]*TimedEvent)
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
			proc, _ := strconv.Atoi(args[3])
			startTime, _ := strconv.ParseInt(args[2], 10, 64)
			value, _ := strconv.Atoi(args[1])
			newEvent := TimedEvent{
				ClientId:  proc,
				StartTime: startTime,
				Operation: Operation{
					OperationType: write,
					Payload:       value,
				},
			}
			events = append(events, &newEvent)
			procIdMap[proc] = &newEvent
		case invokeCas.MatchString(line):
			args := invokeCas.FindStringSubmatch(line)
			proc, _ := strconv.Atoi(args[4])
			startTime, _ := strconv.ParseInt(args[3], 10, 64)
			from, _ := strconv.Atoi(args[1])
			to, _ := strconv.Atoi(args[2])
			newEvent := TimedEvent{
				ClientId:  proc,
				StartTime: startTime,
				Operation: Operation{
					OperationType: cas,
					Payload:       []int{from, to},
				},
			}
			events = append(events, &newEvent)
			procIdMap[proc] = &newEvent
		case returnWrite.MatchString(line):
			args := returnWrite.FindStringSubmatch(line)
			proc, _ := strconv.Atoi(args[2])
			endTime, _ := strconv.ParseInt(args[1], 10, 64)
			event := procIdMap[proc]
			delete(procIdMap, proc)
			event.EndTime = endTime
			event.Status = ok
		case returnCas.MatchString(line):
			args := returnCas.FindStringSubmatch(line)
			proc, _ := strconv.Atoi(args[3])
			endTime, _ := strconv.ParseInt(args[2], 10, 64)
			status := args[1]
			event := procIdMap[proc]
			event.EndTime = endTime
			if status == "ok" {
				event.Status = ok
			} else {
				event.Status = fail
			}
			delete(procIdMap, proc)
		}
	}

	return events
}

func categorizeEvents(okEvents []*TimedEvent) (ResultType, *TimedEvent, map[int]*TimedEvent) {
	var firstWrite *TimedEvent = nil
	readV2Event := make(map[int]*TimedEvent)
	for _, okEvent := range okEvents {
		if okEvent.Operation.OperationType == write {
			if firstWrite != nil {
				return repeatedWrite, nil, nil
			} else {
				firstWrite = okEvent
			}
		} else {
			cas := okEvent.Operation.Payload.([]int)
			if _, ok := readV2Event[cas[0]]; ok {
				return repeatedRead, nil, nil
			} else {
				readV2Event[cas[0]] = okEvent
			}
		}
	}
	return valid, firstWrite, readV2Event
}

func checkTime(valuedEvents []*TimedEvent) ResultType {
	maxStartTime := valuedEvents[0].StartTime
	for i := 1; i < len(valuedEvents); i++ {
		if valuedEvents[i].EndTime < maxStartTime {
			return invalidTime
		}
		if valuedEvents[i].StartTime > maxStartTime {
			maxStartTime = valuedEvents[i].StartTime
		}
	}
	return valid
}

func checkFile(filename string) ResultType {
	events := parseCASJepsenLog(filename)
	var okEvents []*TimedEvent
	for _, event := range events {
		if event.Status == ok {
			okEvents = append(okEvents, event)
		}
	}
	result, firstWrite, readV2Event := categorizeEvents(okEvents)
	if result != valid {
		return result
	}

	valuedEvents := []*TimedEvent{firstWrite}
	readValue := firstWrite.Operation.Payload.(int)
	event, ok := readV2Event[readValue]
	for ok {
		valuedEvents = append(valuedEvents, event)
		cas := event.Operation.Payload.([]int)
		readValue = cas[1]
		event, ok = readV2Event[readValue]
	}
	if len(valuedEvents) != len(okEvents) {
		return incompleteCASChain
	}

	return checkTime(valuedEvents)
}

func main() {
	filename := os.Args[1]
	start := time.Now()
	checkFile(filename)
	elapsed := time.Since(start)
	fmt.Println(filename + "|" + strconv.FormatInt(elapsed.Microseconds(), 10))
}
