import { Button } from "@/components/ui/button"
import { PlusCircle } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { stopsApi } from "./api/stops.api"
import { useNavigate } from "@tanstack/react-router"

function Stops() {
    const apidate = useQuery({
        queryKey: ["stops"],
        queryFn: () => stopsApi.getAll(),
    })
    const navigate = useNavigate()
    console.log(apidate.data)
    return (
        <div className="">
            <div className="flex flex-row justify-between">
                <h1 className="text-xl font-bold">
                    Stops management
                </h1>
                <Button className="cursor-pointer" onClick={() => navigate({
                    to: "/stops/new",
                })}>
                    <PlusCircle />
                    Add stop
                </Button>
            </div>
        </div>
    )
}
export default Stops