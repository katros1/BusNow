import { Button } from "@/components/ui/button"
import { PlusCircle } from "lucide-react"

function Stops() {
    return (
        <div className="">
            <div className="flex flex-row justify-between">
                <h1 className="text-xl font-bold">
                    Stops management
                </h1>
                <Button className="cursor-pointer">
                    <PlusCircle />
                    Add stop
                </Button>
            </div>
        </div>
    )
}
export default Stops