#!/bin/bash

#===============================================================================
# FHIR Patient Journey Script
# Creates a complete, realistic patient healthcare journey
#
# Server: http://216.48.189.175:8080/fhir
#===============================================================================

set -e

# Configuration
FHIR_SERVER="http://216.48.189.175:8080/fhir"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Store created resource IDs (using simple variables for compatibility)
ORG_ID=""
PRACTITIONER_ID=""
PATIENT_ID=""
ENCOUNTER_ID=""
ALLERGY1_ID=""
ALLERGY2_ID=""
BP_ID=""
HR_ID=""
TROPONIN_ID=""
CHOLESTEROL_ID=""
BNP_ID=""
DIAGNOSTIC_REPORT_ID=""
HYPERTENSION_ID=""
ANGINA_ID=""
LISINOPRIL_ID=""
PROCEDURE_ID=""
CAREPLAN_ID=""
IMMUNIZATION_ID=""

#===============================================================================
# PATIENT JOURNEY DIAGRAM
#===============================================================================
print_journey_diagram() {
    echo -e "${CYAN}"
    cat << 'EOF'
================================================================================
                        PATIENT HEALTHCARE JOURNEY
================================================================================

    PATIENT: John Smith (DOB: 1985-03-15)
    ============================================

    DAY 1: Initial Registration & Emergency Visit
    ┌─────────────────────────────────────────────────────────────────────────┐
    │                                                                         │
    │   ┌──────────┐     ┌──────────────┐     ┌─────────────────┐            │
    │   │ PATIENT  │────>│ ORGANIZATION │────>│  PRACTITIONER   │            │
    │   │  John    │     │  City General│     │  Dr. Sarah Chen │            │
    │   │  Smith   │     │   Hospital   │     │  (Primary Care) │            │
    │   └──────────┘     └──────────────┘     └─────────────────┘            │
    │        │                                         │                      │
    │        │         ┌───────────────────────────────┘                      │
    │        │         │                                                      │
    │        v         v                                                      │
    │   ┌─────────────────────────────────────────────────────────────┐      │
    │   │                    ENCOUNTER (Emergency)                     │      │
    │   │  Chief Complaint: Severe chest pain, shortness of breath    │      │
    │   └─────────────────────────────────────────────────────────────┘      │
    │                              │                                          │
    └──────────────────────────────│──────────────────────────────────────────┘
                                   │
    DAY 1: Assessment & Diagnosis  │
    ┌──────────────────────────────│──────────────────────────────────────────┐
    │                              v                                          │
    │   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐        │
    │   │  ALLERGY        │  │  OBSERVATION    │  │  OBSERVATION    │        │
    │   │  INTOLERANCE    │  │  (Vitals)       │  │  (Lab Results)  │        │
    │   │  - Penicillin   │  │  - BP: 150/95   │  │  - Troponin     │        │
    │   │  - Shellfish    │  │  - HR: 98 bpm   │  │  - Cholesterol  │        │
    │   └─────────────────┘  │  - Temp: 98.6°F │  │  - BNP          │        │
    │                        │  - SpO2: 96%    │  └─────────────────┘        │
    │                        └─────────────────┘           │                  │
    │                                                      v                  │
    │                              ┌─────────────────────────────────────┐    │
    │                              │       DIAGNOSTIC REPORT             │    │
    │                              │       Cardiac Panel Results         │    │
    │                              └─────────────────────────────────────┘    │
    │                                              │                          │
    │                                              v                          │
    │                              ┌─────────────────────────────────────┐    │
    │                              │          CONDITION                  │    │
    │                              │    Diagnosis: Hypertension &        │    │
    │                              │    Unstable Angina                  │    │
    │                              └─────────────────────────────────────┘    │
    │                                                                         │
    └─────────────────────────────────────────────────────────────────────────┘

    DAY 2: Treatment Plan
    ┌─────────────────────────────────────────────────────────────────────────┐
    │                                                                         │
    │   ┌─────────────────────┐     ┌─────────────────────┐                  │
    │   │ MEDICATION REQUEST  │     │ MEDICATION REQUEST  │                  │
    │   │ - Lisinopril 10mg   │     │ - Aspirin 81mg      │                  │
    │   │   (Blood Pressure)  │     │   (Blood Thinner)   │                  │
    │   └─────────────────────┘     └─────────────────────┘                  │
    │                                                                         │
    │   ┌─────────────────────┐     ┌─────────────────────┐                  │
    │   │ MEDICATION REQUEST  │     │     PROCEDURE       │                  │
    │   │ - Atorvastatin 40mg │     │  Cardiac Stress     │                  │
    │   │   (Cholesterol)     │     │  Test Performed     │                  │
    │   └─────────────────────┘     └─────────────────────┘                  │
    │                                                                         │
    └─────────────────────────────────────────────────────────────────────────┘

    DAY 3: Follow-up Care
    ┌─────────────────────────────────────────────────────────────────────────┐
    │                                                                         │
    │   ┌─────────────────────────────────────────────────────────────────┐  │
    │   │                        CARE PLAN                                │  │
    │   │  Goals:                                                         │  │
    │   │  - Blood pressure control (<130/80)                            │  │
    │   │  - Cholesterol management                                       │  │
    │   │  - Lifestyle modifications                                      │  │
    │   │                                                                 │  │
    │   │  Activities:                                                    │  │
    │   │  - Daily medication adherence                                   │  │
    │   │  - Low-sodium diet                                              │  │
    │   │  - 30 min exercise daily                                        │  │
    │   │  - Follow-up in 2 weeks                                         │  │
    │   └─────────────────────────────────────────────────────────────────┘  │
    │                                                                         │
    │   ┌─────────────────────┐                                              │
    │   │    IMMUNIZATION     │                                              │
    │   │  Flu Vaccine Given  │                                              │
    │   │  (Preventive Care)  │                                              │
    │   └─────────────────────┘                                              │
    │                                                                         │
    └─────────────────────────────────────────────────────────────────────────┘

================================================================================
                           RESOURCE RELATIONSHIPS
================================================================================

                              ┌──────────────┐
                              │   PATIENT    │
                              │  John Smith  │
                              └──────┬───────┘
                                     │
           ┌─────────────────────────┼─────────────────────────┐
           │                         │                         │
           v                         v                         v
    ┌──────────────┐         ┌──────────────┐         ┌──────────────┐
    │ ORGANIZATION │         │  ENCOUNTER   │         │ PRACTITIONER │
    │City General  │<────────│  Emergency   │────────>│ Dr. S. Chen  │
    └──────────────┘         └──────┬───────┘         └──────────────┘
                                    │
        ┌───────────────┬───────────┼───────────┬───────────────┐
        │               │           │           │               │
        v               v           v           v               v
 ┌────────────┐  ┌────────────┐ ┌────────┐ ┌────────────┐ ┌──────────┐
 │ ALLERGY    │  │OBSERVATION │ │CONDITION│ │ DIAGNOSTIC │ │PROCEDURE │
 │INTOLERANCE │  │  (Vitals)  │ │(Diagnosis)│ REPORT     │ │          │
 └────────────┘  └────────────┘ └────────┘ └────────────┘ └──────────┘
                                    │
                                    v
                        ┌───────────────────────┐
                        │  MEDICATION REQUESTS  │
                        │  (3 Prescriptions)    │
                        └───────────────────────┘
                                    │
                                    v
                            ┌──────────────┐
                            │  CARE PLAN   │
                            └──────────────┘

================================================================================
EOF
    echo -e "${NC}"
}

#===============================================================================
# Helper Functions
#===============================================================================

log_step() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${MAGENTA}STEP: $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_info() {
    echo -e "${CYAN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

create_resource() {
    local resource_type=$1
    local data=$2
    local description=$3

    log_info "Creating $resource_type: $description"

    response=$(curl -s -X POST "$FHIR_SERVER/$resource_type" \
        -H "Content-Type: application/json" \
        -H "Accept: application/json" \
        -d "$data")

    # Extract ID from response
    LAST_CREATED_ID=$(echo "$response" | grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/"id"[[:space:]]*:[[:space:]]*"\([^"]*\)"/\1/')

    if [ -n "$LAST_CREATED_ID" ] && [ "$LAST_CREATED_ID" != "null" ]; then
        log_success "Created $resource_type with ID: $LAST_CREATED_ID"
        echo -e "${YELLOW}  URL: $FHIR_SERVER/$resource_type/$LAST_CREATED_ID${NC}"
    else
        log_error "Failed to create $resource_type"
        echo "$response" | head -20
        return 1
    fi
}

#===============================================================================
# Main Execution
#===============================================================================

echo -e "${GREEN}"
echo "================================================================================"
echo "              FHIR PATIENT JOURNEY - COMPLETE HEALTHCARE SCENARIO"
echo "================================================================================"
echo -e "${NC}"
echo ""
echo "Server: $FHIR_SERVER"
echo "Date: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Print the journey diagram first
print_journey_diagram

echo ""
echo -e "${YELLOW}Press Enter to start creating resources...${NC}"
read -r

#===============================================================================
# 1. CREATE ORGANIZATION (Hospital)
#===============================================================================
log_step "1. Creating Organization (City General Hospital)"

ORGANIZATION_DATA='{
  "resourceType": "Organization",
  "identifier": [
    {
      "system": "http://hl7.org/fhir/sid/us-npi",
      "value": "1234567890"
    }
  ],
  "active": true,
  "type": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/organization-type",
          "code": "prov",
          "display": "Healthcare Provider"
        }
      ]
    }
  ],
  "name": "City General Hospital",
  "telecom": [
    {
      "system": "phone",
      "value": "+1-555-123-4567",
      "use": "work"
    },
    {
      "system": "email",
      "value": "info@citygeneralhospital.org",
      "use": "work"
    }
  ],
  "address": [
    {
      "use": "work",
      "type": "physical",
      "line": ["123 Healthcare Avenue"],
      "city": "Medical City",
      "state": "CA",
      "postalCode": "90210",
      "country": "USA"
    }
  ]
}'

create_resource "Organization" "$ORGANIZATION_DATA" "City General Hospital"
ORG_ID=$LAST_CREATED_ID

#===============================================================================
# 2. CREATE PRACTITIONER (Doctor)
#===============================================================================
log_step "2. Creating Practitioner (Dr. Sarah Chen)"

PRACTITIONER_DATA='{
  "resourceType": "Practitioner",
  "identifier": [
    {
      "system": "http://hl7.org/fhir/sid/us-npi",
      "value": "9876543210"
    }
  ],
  "active": true,
  "name": [
    {
      "use": "official",
      "family": "Chen",
      "given": ["Sarah", "Elizabeth"],
      "prefix": ["Dr."],
      "suffix": ["MD", "FACC"]
    }
  ],
  "telecom": [
    {
      "system": "phone",
      "value": "+1-555-987-6543",
      "use": "work"
    },
    {
      "system": "email",
      "value": "dr.chen@citygeneralhospital.org",
      "use": "work"
    }
  ],
  "address": [
    {
      "use": "work",
      "line": ["123 Healthcare Avenue", "Suite 500"],
      "city": "Medical City",
      "state": "CA",
      "postalCode": "90210",
      "country": "USA"
    }
  ],
  "gender": "female",
  "birthDate": "1975-08-22",
  "qualification": [
    {
      "identifier": [
        {
          "system": "http://example.org/UniversityIdentifier",
          "value": "MD-2000-12345"
        }
      ],
      "code": {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0360",
            "code": "MD",
            "display": "Doctor of Medicine"
          }
        ],
        "text": "Doctor of Medicine"
      },
      "period": {
        "start": "2000-06-15"
      },
      "issuer": {
        "display": "Stanford University School of Medicine"
      }
    },
    {
      "code": {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0360",
            "code": "FACC",
            "display": "Fellow of American College of Cardiology"
          }
        ],
        "text": "Board Certified Cardiologist"
      },
      "period": {
        "start": "2005-09-01"
      },
      "issuer": {
        "display": "American College of Cardiology"
      }
    }
  ]
}'

create_resource "Practitioner" "$PRACTITIONER_DATA" "Dr. Sarah Chen (Cardiologist)"
PRACTITIONER_ID=$LAST_CREATED_ID

#===============================================================================
# 3. CREATE PATIENT
#===============================================================================
log_step "3. Creating Patient (John Smith)"

PATIENT_DATA='{
  "resourceType": "Patient",
  "identifier": [
    {
      "use": "usual",
      "type": {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
            "code": "MR",
            "display": "Medical Record Number"
          }
        ]
      },
      "system": "http://citygeneralhospital.org/mrn",
      "value": "MRN-2024-001234"
    },
    {
      "use": "official",
      "type": {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
            "code": "SS",
            "display": "Social Security Number"
          }
        ]
      },
      "system": "http://hl7.org/fhir/sid/us-ssn",
      "value": "123-45-6789"
    }
  ],
  "active": true,
  "name": [
    {
      "use": "official",
      "family": "Smith",
      "given": ["John", "Michael"],
      "prefix": ["Mr."]
    },
    {
      "use": "nickname",
      "given": ["Johnny"]
    }
  ],
  "telecom": [
    {
      "system": "phone",
      "value": "+1-555-234-5678",
      "use": "mobile",
      "rank": 1
    },
    {
      "system": "phone",
      "value": "+1-555-234-5679",
      "use": "home"
    },
    {
      "system": "email",
      "value": "john.smith@email.com",
      "use": "home"
    }
  ],
  "gender": "male",
  "birthDate": "1985-03-15",
  "address": [
    {
      "use": "home",
      "type": "physical",
      "line": ["456 Oak Street", "Apt 2B"],
      "city": "Medical City",
      "state": "CA",
      "postalCode": "90211",
      "country": "USA"
    }
  ],
  "maritalStatus": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/v3-MaritalStatus",
        "code": "M",
        "display": "Married"
      }
    ]
  },
  "contact": [
    {
      "relationship": [
        {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
              "code": "N",
              "display": "Next-of-Kin"
            }
          ]
        }
      ],
      "name": {
        "family": "Smith",
        "given": ["Jane"]
      },
      "telecom": [
        {
          "system": "phone",
          "value": "+1-555-234-5680",
          "use": "mobile"
        }
      ]
    }
  ],
  "communication": [
    {
      "language": {
        "coding": [
          {
            "system": "urn:ietf:bcp:47",
            "code": "en-US",
            "display": "English (United States)"
          }
        ]
      },
      "preferred": true
    }
  ],
  "generalPractitioner": [
    {
      "reference": "Practitioner/'"$PRACTITIONER_ID"'",
      "display": "Dr. Sarah Chen"
    }
  ],
  "managingOrganization": {
    "reference": "Organization/'"$ORG_ID"'",
    "display": "City General Hospital"
  }
}'

create_resource "Patient" "$PATIENT_DATA" "John Smith"
PATIENT_ID=$LAST_CREATED_ID

#===============================================================================
# 4. CREATE ALLERGY INTOLERANCE
#===============================================================================
log_step "4. Creating Allergy Intolerances"

# Penicillin Allergy
ALLERGY1_DATA='{
  "resourceType": "AllergyIntolerance",
  "clinicalStatus": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
        "code": "active",
        "display": "Active"
      }
    ]
  },
  "verificationStatus": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
        "code": "confirmed",
        "display": "Confirmed"
      }
    ]
  },
  "type": "allergy",
  "category": ["medication"],
  "criticality": "high",
  "code": {
    "coding": [
      {
        "system": "http://www.nlm.nih.gov/research/umls/rxnorm",
        "code": "7984",
        "display": "Penicillin"
      }
    ],
    "text": "Penicillin"
  },
  "patient": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "recordedDate": "2020-05-15",
  "recorder": {
    "reference": "Practitioner/'"$PRACTITIONER_ID"'",
    "display": "Dr. Sarah Chen"
  },
  "reaction": [
    {
      "substance": {
        "coding": [
          {
            "system": "http://www.nlm.nih.gov/research/umls/rxnorm",
            "code": "7984",
            "display": "Penicillin"
          }
        ]
      },
      "manifestation": [
        {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "39579001",
              "display": "Anaphylaxis"
            }
          ]
        }
      ],
      "severity": "severe"
    }
  ]
}'

create_resource "AllergyIntolerance" "$ALLERGY1_DATA" "Penicillin Allergy (High Criticality)"
ALLERGY1_ID=$LAST_CREATED_ID

# Shellfish Allergy
ALLERGY2_DATA='{
  "resourceType": "AllergyIntolerance",
  "clinicalStatus": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
        "code": "active",
        "display": "Active"
      }
    ]
  },
  "verificationStatus": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
        "code": "confirmed",
        "display": "Confirmed"
      }
    ]
  },
  "type": "allergy",
  "category": ["food"],
  "criticality": "low",
  "code": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "227426003",
        "display": "Shellfish"
      }
    ],
    "text": "Shellfish"
  },
  "patient": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "recordedDate": "2018-03-20",
  "reaction": [
    {
      "manifestation": [
        {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "126485001",
              "display": "Urticaria"
            }
          ]
        }
      ],
      "severity": "moderate"
    }
  ]
}'

create_resource "AllergyIntolerance" "$ALLERGY2_DATA" "Shellfish Allergy (Food)"

#===============================================================================
# 5. CREATE ENCOUNTER (Emergency Visit)
#===============================================================================
log_step "5. Creating Encounter (Emergency Department Visit)"

ENCOUNTER_DATA='{
  "resourceType": "Encounter",
  "identifier": [
    {
      "use": "official",
      "system": "http://citygeneralhospital.org/encounter",
      "value": "ENC-2024-ER-001234"
    }
  ],
  "status": "finished",
  "class": {
    "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
    "code": "EMER",
    "display": "emergency"
  },
  "type": [
    {
      "coding": [
        {
          "system": "http://snomed.info/sct",
          "code": "50849002",
          "display": "Emergency department patient visit"
        }
      ]
    }
  ],
  "priority": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/v3-ActPriority",
        "code": "EM",
        "display": "emergency"
      }
    ]
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "participant": [
    {
      "type": [
        {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/v3-ParticipationType",
              "code": "ATND",
              "display": "attender"
            }
          ]
        }
      ],
      "individual": {
        "reference": "Practitioner/'"$PRACTITIONER_ID"'",
        "display": "Dr. Sarah Chen"
      }
    }
  ],
  "period": {
    "start": "2024-01-15T08:30:00Z",
    "end": "2024-01-17T14:00:00Z"
  },
  "reasonCode": [
    {
      "coding": [
        {
          "system": "http://snomed.info/sct",
          "code": "29857009",
          "display": "Chest pain"
        }
      ],
      "text": "Severe chest pain and shortness of breath"
    }
  ],
  "hospitalization": {
    "admitSource": {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/admit-source",
          "code": "emd",
          "display": "From accident/emergency department"
        }
      ]
    },
    "dischargeDisposition": {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/discharge-disposition",
          "code": "home",
          "display": "Home"
        }
      ]
    }
  },
  "serviceProvider": {
    "reference": "Organization/'"$ORG_ID"'",
    "display": "City General Hospital"
  }
}'

create_resource "Encounter" "$ENCOUNTER_DATA" "Emergency Department Visit"
ENCOUNTER_ID=$LAST_CREATED_ID

#===============================================================================
# 6. CREATE OBSERVATIONS (Vital Signs)
#===============================================================================
log_step "6. Creating Observations (Vital Signs)"

# Blood Pressure
BP_DATA='{
  "resourceType": "Observation",
  "status": "final",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/observation-category",
          "code": "vital-signs",
          "display": "Vital Signs"
        }
      ]
    }
  ],
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "85354-9",
        "display": "Blood pressure panel"
      }
    ],
    "text": "Blood Pressure"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "effectiveDateTime": "2024-01-15T09:00:00Z",
  "performer": [
    {
      "reference": "Practitioner/'"$PRACTITIONER_ID"'",
      "display": "Dr. Sarah Chen"
    }
  ],
  "component": [
    {
      "code": {
        "coding": [
          {
            "system": "http://loinc.org",
            "code": "8480-6",
            "display": "Systolic blood pressure"
          }
        ]
      },
      "valueQuantity": {
        "value": 150,
        "unit": "mmHg",
        "system": "http://unitsofmeasure.org",
        "code": "mm[Hg]"
      },
      "interpretation": [
        {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
              "code": "H",
              "display": "High"
            }
          ]
        }
      ]
    },
    {
      "code": {
        "coding": [
          {
            "system": "http://loinc.org",
            "code": "8462-4",
            "display": "Diastolic blood pressure"
          }
        ]
      },
      "valueQuantity": {
        "value": 95,
        "unit": "mmHg",
        "system": "http://unitsofmeasure.org",
        "code": "mm[Hg]"
      },
      "interpretation": [
        {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
              "code": "H",
              "display": "High"
            }
          ]
        }
      ]
    }
  ]
}'

create_resource "Observation" "$BP_DATA" "Blood Pressure: 150/95 mmHg (High)"
BP_ID=$LAST_CREATED_ID

# Heart Rate
HR_DATA='{
  "resourceType": "Observation",
  "status": "final",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/observation-category",
          "code": "vital-signs",
          "display": "Vital Signs"
        }
      ]
    }
  ],
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "8867-4",
        "display": "Heart rate"
      }
    ],
    "text": "Heart Rate"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "effectiveDateTime": "2024-01-15T09:00:00Z",
  "valueQuantity": {
    "value": 98,
    "unit": "beats/minute",
    "system": "http://unitsofmeasure.org",
    "code": "/min"
  },
  "interpretation": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
          "code": "H",
          "display": "High"
        }
      ]
    }
  ]
}'

create_resource "Observation" "$HR_DATA" "Heart Rate: 98 bpm (Elevated)"
HR_ID=$LAST_CREATED_ID

# Oxygen Saturation
SPO2_DATA='{
  "resourceType": "Observation",
  "status": "final",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/observation-category",
          "code": "vital-signs",
          "display": "Vital Signs"
        }
      ]
    }
  ],
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "2708-6",
        "display": "Oxygen saturation"
      }
    ],
    "text": "Oxygen Saturation (SpO2)"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "effectiveDateTime": "2024-01-15T09:00:00Z",
  "valueQuantity": {
    "value": 96,
    "unit": "%",
    "system": "http://unitsofmeasure.org",
    "code": "%"
  }
}'

create_resource "Observation" "$SPO2_DATA" "SpO2: 96%"

# Body Temperature
TEMP_DATA='{
  "resourceType": "Observation",
  "status": "final",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/observation-category",
          "code": "vital-signs",
          "display": "Vital Signs"
        }
      ]
    }
  ],
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "8310-5",
        "display": "Body temperature"
      }
    ],
    "text": "Body Temperature"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "effectiveDateTime": "2024-01-15T09:00:00Z",
  "valueQuantity": {
    "value": 98.6,
    "unit": "degF",
    "system": "http://unitsofmeasure.org",
    "code": "[degF]"
  }
}'

create_resource "Observation" "$TEMP_DATA" "Temperature: 98.6°F"

#===============================================================================
# 7. CREATE OBSERVATIONS (Lab Results)
#===============================================================================
log_step "7. Creating Observations (Laboratory Results)"

# Troponin
TROPONIN_DATA='{
  "resourceType": "Observation",
  "status": "final",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/observation-category",
          "code": "laboratory",
          "display": "Laboratory"
        }
      ]
    }
  ],
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "6598-7",
        "display": "Troponin T cardiac"
      }
    ],
    "text": "Cardiac Troponin T"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "effectiveDateTime": "2024-01-15T10:30:00Z",
  "valueQuantity": {
    "value": 0.08,
    "unit": "ng/mL",
    "system": "http://unitsofmeasure.org",
    "code": "ng/mL"
  },
  "interpretation": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
          "code": "H",
          "display": "High"
        }
      ]
    }
  ],
  "referenceRange": [
    {
      "high": {
        "value": 0.04,
        "unit": "ng/mL"
      },
      "text": "Normal: < 0.04 ng/mL"
    }
  ]
}'

create_resource "Observation" "$TROPONIN_DATA" "Troponin T: 0.08 ng/mL (Elevated)"
TROPONIN_ID=$LAST_CREATED_ID

# Total Cholesterol
CHOLESTEROL_DATA='{
  "resourceType": "Observation",
  "status": "final",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/observation-category",
          "code": "laboratory",
          "display": "Laboratory"
        }
      ]
    }
  ],
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "2093-3",
        "display": "Cholesterol total"
      }
    ],
    "text": "Total Cholesterol"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "effectiveDateTime": "2024-01-15T10:30:00Z",
  "valueQuantity": {
    "value": 265,
    "unit": "mg/dL",
    "system": "http://unitsofmeasure.org",
    "code": "mg/dL"
  },
  "interpretation": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
          "code": "H",
          "display": "High"
        }
      ]
    }
  ],
  "referenceRange": [
    {
      "high": {
        "value": 200,
        "unit": "mg/dL"
      },
      "text": "Desirable: < 200 mg/dL"
    }
  ]
}'

create_resource "Observation" "$CHOLESTEROL_DATA" "Total Cholesterol: 265 mg/dL (High)"
CHOLESTEROL_ID=$LAST_CREATED_ID

# BNP (Brain Natriuretic Peptide)
BNP_DATA='{
  "resourceType": "Observation",
  "status": "final",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/observation-category",
          "code": "laboratory",
          "display": "Laboratory"
        }
      ]
    }
  ],
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "42637-9",
        "display": "Natriuretic peptide B"
      }
    ],
    "text": "BNP (Brain Natriuretic Peptide)"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "effectiveDateTime": "2024-01-15T10:30:00Z",
  "valueQuantity": {
    "value": 450,
    "unit": "pg/mL",
    "system": "http://unitsofmeasure.org",
    "code": "pg/mL"
  },
  "interpretation": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
          "code": "H",
          "display": "High"
        }
      ]
    }
  ],
  "referenceRange": [
    {
      "high": {
        "value": 100,
        "unit": "pg/mL"
      },
      "text": "Normal: < 100 pg/mL"
    }
  ]
}'

create_resource "Observation" "$BNP_DATA" "BNP: 450 pg/mL (Elevated)"
BNP_ID=$LAST_CREATED_ID

#===============================================================================
# 8. CREATE DIAGNOSTIC REPORT
#===============================================================================
log_step "8. Creating Diagnostic Report (Cardiac Panel)"

DIAGNOSTIC_REPORT_DATA='{
  "resourceType": "DiagnosticReport",
  "identifier": [
    {
      "system": "http://citygeneralhospital.org/lab-report",
      "value": "LAB-2024-001234"
    }
  ],
  "status": "final",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/v2-0074",
          "code": "LAB",
          "display": "Laboratory"
        },
        {
          "system": "http://terminology.hl7.org/CodeSystem/v2-0074",
          "code": "CH",
          "display": "Chemistry"
        }
      ]
    }
  ],
  "code": {
    "coding": [
      {
        "system": "http://loinc.org",
        "code": "24331-1",
        "display": "Cardiac panel"
      }
    ],
    "text": "Cardiac Biomarker Panel"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "effectiveDateTime": "2024-01-15T11:00:00Z",
  "issued": "2024-01-15T12:30:00Z",
  "performer": [
    {
      "reference": "Organization/'"$ORG_ID"'",
      "display": "City General Hospital Laboratory"
    }
  ],
  "result": [
    {
      "reference": "Observation/'"$TROPONIN_ID"'",
      "display": "Troponin T: 0.08 ng/mL (H)"
    },
    {
      "reference": "Observation/'"$BNP_ID"'",
      "display": "BNP: 450 pg/mL (H)"
    },
    {
      "reference": "Observation/'"$CHOLESTEROL_ID"'",
      "display": "Total Cholesterol: 265 mg/dL (H)"
    }
  ],
  "conclusion": "Elevated cardiac biomarkers consistent with acute coronary syndrome. Elevated cholesterol indicating hyperlipidemia. Recommend cardiology consultation and aggressive medical management.",
  "conclusionCode": [
    {
      "coding": [
        {
          "system": "http://snomed.info/sct",
          "code": "394659003",
          "display": "Acute coronary syndrome"
        }
      ]
    }
  ]
}'

create_resource "DiagnosticReport" "$DIAGNOSTIC_REPORT_DATA" "Cardiac Panel Results"
DIAGNOSTIC_REPORT_ID=$LAST_CREATED_ID

#===============================================================================
# 9. CREATE CONDITIONS (Diagnoses)
#===============================================================================
log_step "9. Creating Conditions (Diagnoses)"

# Hypertension
HYPERTENSION_DATA='{
  "resourceType": "Condition",
  "clinicalStatus": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
        "code": "active",
        "display": "Active"
      }
    ]
  },
  "verificationStatus": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/condition-ver-status",
        "code": "confirmed",
        "display": "Confirmed"
      }
    ]
  },
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/condition-category",
          "code": "encounter-diagnosis",
          "display": "Encounter Diagnosis"
        }
      ]
    }
  ],
  "severity": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "24484000",
        "display": "Severe"
      }
    ]
  },
  "code": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "38341003",
        "display": "Hypertensive disorder"
      },
      {
        "system": "http://hl7.org/fhir/sid/icd-10-cm",
        "code": "I10",
        "display": "Essential (primary) hypertension"
      }
    ],
    "text": "Essential Hypertension"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "onsetDateTime": "2024-01-15",
  "recordedDate": "2024-01-15",
  "recorder": {
    "reference": "Practitioner/'"$PRACTITIONER_ID"'",
    "display": "Dr. Sarah Chen"
  },
  "note": [
    {
      "text": "Patient presented with BP 150/95. History of uncontrolled hypertension. Started on Lisinopril."
    }
  ]
}'

create_resource "Condition" "$HYPERTENSION_DATA" "Essential Hypertension (I10)"
HYPERTENSION_ID=$LAST_CREATED_ID

# Unstable Angina
ANGINA_DATA='{
  "resourceType": "Condition",
  "clinicalStatus": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
        "code": "active",
        "display": "Active"
      }
    ]
  },
  "verificationStatus": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/condition-ver-status",
        "code": "confirmed",
        "display": "Confirmed"
      }
    ]
  },
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/condition-category",
          "code": "encounter-diagnosis",
          "display": "Encounter Diagnosis"
        }
      ]
    }
  ],
  "severity": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "24484000",
        "display": "Severe"
      }
    ]
  },
  "code": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "25106000",
        "display": "Unstable angina"
      },
      {
        "system": "http://hl7.org/fhir/sid/icd-10-cm",
        "code": "I20.0",
        "display": "Unstable angina"
      }
    ],
    "text": "Unstable Angina"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "onsetDateTime": "2024-01-15",
  "recordedDate": "2024-01-15",
  "recorder": {
    "reference": "Practitioner/'"$PRACTITIONER_ID"'",
    "display": "Dr. Sarah Chen"
  },
  "evidence": [
    {
      "code": [
        {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "29857009",
              "display": "Chest pain"
            }
          ]
        }
      ]
    }
  ],
  "note": [
    {
      "text": "Acute chest pain with elevated troponin. EKG shows ST depression. Started on dual antiplatelet therapy."
    }
  ]
}'

create_resource "Condition" "$ANGINA_DATA" "Unstable Angina (I20.0)"
ANGINA_ID=$LAST_CREATED_ID

#===============================================================================
# 10. CREATE MEDICATION REQUESTS
#===============================================================================
log_step "10. Creating Medication Requests (Prescriptions)"

# Lisinopril
LISINOPRIL_DATA='{
  "resourceType": "MedicationRequest",
  "identifier": [
    {
      "system": "http://citygeneralhospital.org/prescription",
      "value": "RX-2024-001234-01"
    }
  ],
  "status": "active",
  "intent": "order",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/medicationrequest-category",
          "code": "outpatient",
          "display": "Outpatient"
        }
      ]
    }
  ],
  "priority": "routine",
  "medicationCodeableConcept": {
    "coding": [
      {
        "system": "http://www.nlm.nih.gov/research/umls/rxnorm",
        "code": "314076",
        "display": "Lisinopril 10 MG Oral Tablet"
      }
    ],
    "text": "Lisinopril 10mg tablet"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "authoredOn": "2024-01-16T10:00:00Z",
  "requester": {
    "reference": "Practitioner/'"$PRACTITIONER_ID"'",
    "display": "Dr. Sarah Chen"
  },
  "reasonReference": [
    {
      "reference": "Condition/'"$HYPERTENSION_ID"'",
      "display": "Essential Hypertension"
    }
  ],
  "dosageInstruction": [
    {
      "sequence": 1,
      "text": "Take 1 tablet by mouth once daily in the morning",
      "timing": {
        "repeat": {
          "frequency": 1,
          "period": 1,
          "periodUnit": "d",
          "when": ["MORN"]
        }
      },
      "route": {
        "coding": [
          {
            "system": "http://snomed.info/sct",
            "code": "26643006",
            "display": "Oral route"
          }
        ]
      },
      "doseAndRate": [
        {
          "type": {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/dose-rate-type",
                "code": "ordered",
                "display": "Ordered"
              }
            ]
          },
          "doseQuantity": {
            "value": 1,
            "unit": "tablet",
            "system": "http://terminology.hl7.org/CodeSystem/v3-orderableDrugForm",
            "code": "TAB"
          }
        }
      ]
    }
  ],
  "dispenseRequest": {
    "numberOfRepeatsAllowed": 3,
    "quantity": {
      "value": 30,
      "unit": "tablets"
    },
    "expectedSupplyDuration": {
      "value": 30,
      "unit": "days",
      "system": "http://unitsofmeasure.org",
      "code": "d"
    }
  },
  "substitution": {
    "allowedBoolean": true,
    "reason": {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/v3-substanceAdminSubstitution",
          "code": "G",
          "display": "generic composition"
        }
      ]
    }
  }
}'

create_resource "MedicationRequest" "$LISINOPRIL_DATA" "Lisinopril 10mg (Blood Pressure)"
LISINOPRIL_ID=$LAST_CREATED_ID

# Aspirin
ASPIRIN_DATA='{
  "resourceType": "MedicationRequest",
  "identifier": [
    {
      "system": "http://citygeneralhospital.org/prescription",
      "value": "RX-2024-001234-02"
    }
  ],
  "status": "active",
  "intent": "order",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/medicationrequest-category",
          "code": "outpatient",
          "display": "Outpatient"
        }
      ]
    }
  ],
  "priority": "urgent",
  "medicationCodeableConcept": {
    "coding": [
      {
        "system": "http://www.nlm.nih.gov/research/umls/rxnorm",
        "code": "243670",
        "display": "Aspirin 81 MG Oral Tablet"
      }
    ],
    "text": "Aspirin 81mg (Baby Aspirin)"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "authoredOn": "2024-01-16T10:00:00Z",
  "requester": {
    "reference": "Practitioner/'"$PRACTITIONER_ID"'",
    "display": "Dr. Sarah Chen"
  },
  "reasonReference": [
    {
      "reference": "Condition/'"$ANGINA_ID"'",
      "display": "Unstable Angina"
    }
  ],
  "dosageInstruction": [
    {
      "sequence": 1,
      "text": "Take 1 tablet by mouth once daily",
      "timing": {
        "repeat": {
          "frequency": 1,
          "period": 1,
          "periodUnit": "d"
        }
      },
      "route": {
        "coding": [
          {
            "system": "http://snomed.info/sct",
            "code": "26643006",
            "display": "Oral route"
          }
        ]
      },
      "doseAndRate": [
        {
          "doseQuantity": {
            "value": 81,
            "unit": "mg",
            "system": "http://unitsofmeasure.org",
            "code": "mg"
          }
        }
      ]
    }
  ],
  "dispenseRequest": {
    "numberOfRepeatsAllowed": 11,
    "quantity": {
      "value": 30,
      "unit": "tablets"
    },
    "expectedSupplyDuration": {
      "value": 30,
      "unit": "days"
    }
  }
}'

create_resource "MedicationRequest" "$ASPIRIN_DATA" "Aspirin 81mg (Blood Thinner)"

# Atorvastatin
ATORVASTATIN_DATA='{
  "resourceType": "MedicationRequest",
  "identifier": [
    {
      "system": "http://citygeneralhospital.org/prescription",
      "value": "RX-2024-001234-03"
    }
  ],
  "status": "active",
  "intent": "order",
  "category": [
    {
      "coding": [
        {
          "system": "http://terminology.hl7.org/CodeSystem/medicationrequest-category",
          "code": "outpatient",
          "display": "Outpatient"
        }
      ]
    }
  ],
  "priority": "routine",
  "medicationCodeableConcept": {
    "coding": [
      {
        "system": "http://www.nlm.nih.gov/research/umls/rxnorm",
        "code": "617311",
        "display": "Atorvastatin 40 MG Oral Tablet"
      }
    ],
    "text": "Atorvastatin 40mg (Lipitor)"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "authoredOn": "2024-01-16T10:00:00Z",
  "requester": {
    "reference": "Practitioner/'"$PRACTITIONER_ID"'",
    "display": "Dr. Sarah Chen"
  },
  "dosageInstruction": [
    {
      "sequence": 1,
      "text": "Take 1 tablet by mouth at bedtime",
      "timing": {
        "repeat": {
          "frequency": 1,
          "period": 1,
          "periodUnit": "d",
          "when": ["HS"]
        }
      },
      "route": {
        "coding": [
          {
            "system": "http://snomed.info/sct",
            "code": "26643006",
            "display": "Oral route"
          }
        ]
      },
      "doseAndRate": [
        {
          "doseQuantity": {
            "value": 40,
            "unit": "mg",
            "system": "http://unitsofmeasure.org",
            "code": "mg"
          }
        }
      ]
    }
  ],
  "dispenseRequest": {
    "numberOfRepeatsAllowed": 3,
    "quantity": {
      "value": 30,
      "unit": "tablets"
    },
    "expectedSupplyDuration": {
      "value": 30,
      "unit": "days"
    }
  },
  "note": [
    {
      "text": "Monitor for muscle pain/weakness. Check liver enzymes in 6 weeks."
    }
  ]
}'

create_resource "MedicationRequest" "$ATORVASTATIN_DATA" "Atorvastatin 40mg (Cholesterol)"

#===============================================================================
# 11. CREATE PROCEDURE
#===============================================================================
log_step "11. Creating Procedure (Cardiac Stress Test)"

PROCEDURE_DATA='{
  "resourceType": "Procedure",
  "identifier": [
    {
      "system": "http://citygeneralhospital.org/procedure",
      "value": "PROC-2024-001234"
    }
  ],
  "status": "completed",
  "category": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "103693007",
        "display": "Diagnostic procedure"
      }
    ]
  },
  "code": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "76746007",
        "display": "Cardiovascular stress test"
      },
      {
        "system": "http://www.ama-assn.org/go/cpt",
        "code": "93015",
        "display": "Cardiovascular stress test using maximal or submaximal treadmill"
      }
    ],
    "text": "Cardiac Stress Test (Treadmill)"
  },
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "performedDateTime": "2024-01-16T14:00:00Z",
  "performer": [
    {
      "actor": {
        "reference": "Practitioner/'"$PRACTITIONER_ID"'",
        "display": "Dr. Sarah Chen"
      }
    }
  ],
  "location": {
    "display": "Cardiac Testing Laboratory, Room 302"
  },
  "reasonReference": [
    {
      "reference": "Condition/'"$ANGINA_ID"'",
      "display": "Unstable Angina"
    }
  ],
  "outcome": {
    "coding": [
      {
        "system": "http://snomed.info/sct",
        "code": "385669000",
        "display": "Successful"
      }
    ],
    "text": "Test completed successfully. Moderate ischemic changes noted at peak exercise."
  },
  "note": [
    {
      "text": "Patient exercised for 8 minutes on Bruce protocol. Achieved 85% of target heart rate. ST depression of 2mm noted in leads V4-V6 at peak exercise. No arrhythmias. Blood pressure response appropriate."
    }
  ]
}'

create_resource "Procedure" "$PROCEDURE_DATA" "Cardiac Stress Test"
PROCEDURE_ID=$LAST_CREATED_ID

#===============================================================================
# 12. CREATE CARE PLAN
#===============================================================================
log_step "12. Creating Care Plan"

CAREPLAN_DATA='{
  "resourceType": "CarePlan",
  "identifier": [
    {
      "system": "http://citygeneralhospital.org/careplan",
      "value": "CP-2024-001234"
    }
  ],
  "status": "active",
  "intent": "plan",
  "category": [
    {
      "coding": [
        {
          "system": "http://hl7.org/fhir/us/core/CodeSystem/careplan-category",
          "code": "assess-plan",
          "display": "Assessment and Plan"
        }
      ]
    }
  ],
  "title": "Cardiac Care Plan for John Smith",
  "description": "Comprehensive cardiac care plan addressing hypertension, unstable angina, and hyperlipidemia with medication management, lifestyle modifications, and follow-up monitoring.",
  "subject": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "period": {
    "start": "2024-01-17",
    "end": "2024-07-17"
  },
  "created": "2024-01-17",
  "author": {
    "reference": "Practitioner/'"$PRACTITIONER_ID"'",
    "display": "Dr. Sarah Chen"
  },
  "addresses": [
    {
      "reference": "Condition/'"$HYPERTENSION_ID"'",
      "display": "Essential Hypertension"
    },
    {
      "reference": "Condition/'"$ANGINA_ID"'",
      "display": "Unstable Angina"
    }
  ],
  "goal": [
    {
      "display": "Blood pressure below 130/80 mmHg"
    },
    {
      "display": "LDL cholesterol below 70 mg/dL"
    },
    {
      "display": "No recurrence of chest pain"
    },
    {
      "display": "Improved exercise tolerance"
    }
  ],
  "activity": [
    {
      "detail": {
        "kind": "MedicationRequest",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "430193006",
              "display": "Medication adherence"
            }
          ],
          "text": "Daily Medication Adherence"
        },
        "status": "in-progress",
        "description": "Take all prescribed medications daily as directed: Lisinopril 10mg in morning, Aspirin 81mg daily, Atorvastatin 40mg at bedtime"
      }
    },
    {
      "detail": {
        "kind": "NutritionOrder",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "183063000",
              "display": "Low sodium diet education"
            }
          ],
          "text": "Heart-Healthy Diet"
        },
        "status": "in-progress",
        "description": "Follow DASH diet: limit sodium to <2300mg/day, increase fruits/vegetables, whole grains. Limit saturated fats and avoid trans fats."
      }
    },
    {
      "detail": {
        "kind": "ServiceRequest",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "226029000",
              "display": "Exercise"
            }
          ],
          "text": "Regular Exercise Program"
        },
        "status": "in-progress",
        "description": "30 minutes of moderate aerobic exercise (walking, swimming) 5 days per week. Start slowly and gradually increase intensity. Stop if chest pain occurs."
      }
    },
    {
      "detail": {
        "kind": "Appointment",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "390906007",
              "display": "Follow-up visit"
            }
          ],
          "text": "Follow-up Appointments"
        },
        "status": "scheduled",
        "scheduledPeriod": {
          "start": "2024-01-31"
        },
        "description": "Follow-up in 2 weeks to check blood pressure and medication tolerance. Cardiology follow-up in 4 weeks."
      }
    },
    {
      "detail": {
        "kind": "ServiceRequest",
        "code": {
          "coding": [
            {
              "system": "http://snomed.info/sct",
              "code": "252275004",
              "display": "Blood pressure monitoring"
            }
          ],
          "text": "Home Blood Pressure Monitoring"
        },
        "status": "in-progress",
        "description": "Check blood pressure twice daily (morning and evening). Keep a log to bring to follow-up appointments. Call if BP >160/100 or <90/60."
      }
    }
  ],
  "note": [
    {
      "authorReference": {
        "reference": "Practitioner/'"$PRACTITIONER_ID"'",
        "display": "Dr. Sarah Chen"
      },
      "time": "2024-01-17T10:00:00Z",
      "text": "Patient educated on cardiac risk factors and importance of medication compliance. Family (wife Jane) present and engaged in discussion. Patient motivated to make lifestyle changes. Emergency instructions provided: call 911 if chest pain occurs."
    }
  ]
}'

create_resource "CarePlan" "$CAREPLAN_DATA" "Cardiac Care Plan"
CAREPLAN_ID=$LAST_CREATED_ID

#===============================================================================
# 13. CREATE IMMUNIZATION (Preventive Care)
#===============================================================================
log_step "13. Creating Immunization (Flu Vaccine)"

IMMUNIZATION_DATA='{
  "resourceType": "Immunization",
  "identifier": [
    {
      "system": "http://citygeneralhospital.org/immunization",
      "value": "IMM-2024-001234"
    }
  ],
  "status": "completed",
  "vaccineCode": {
    "coding": [
      {
        "system": "http://hl7.org/fhir/sid/cvx",
        "code": "141",
        "display": "Influenza, seasonal, injectable"
      }
    ],
    "text": "Influenza Vaccine (Flu Shot) 2023-2024"
  },
  "patient": {
    "reference": "Patient/'"$PATIENT_ID"'",
    "display": "John Smith"
  },
  "encounter": {
    "reference": "Encounter/'"$ENCOUNTER_ID"'"
  },
  "occurrenceDateTime": "2024-01-17T09:30:00Z",
  "primarySource": true,
  "location": {
    "display": "City General Hospital - Room 205"
  },
  "manufacturer": {
    "display": "Sanofi Pasteur"
  },
  "lotNumber": "FLU2024-ABC123",
  "expirationDate": "2024-06-30",
  "site": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/v3-ActSite",
        "code": "LA",
        "display": "Left arm"
      }
    ]
  },
  "route": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration",
        "code": "IM",
        "display": "Intramuscular"
      }
    ]
  },
  "doseQuantity": {
    "value": 0.5,
    "unit": "mL",
    "system": "http://unitsofmeasure.org",
    "code": "mL"
  },
  "performer": [
    {
      "function": {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0443",
            "code": "AP",
            "display": "Administering Provider"
          }
        ]
      },
      "actor": {
        "reference": "Practitioner/'"$PRACTITIONER_ID"'",
        "display": "Dr. Sarah Chen"
      }
    }
  ],
  "note": [
    {
      "text": "Patient tolerated vaccine well. No immediate adverse reactions. Advised to monitor for local soreness. Recommended for cardiac patients."
    }
  ],
  "reasonCode": [
    {
      "coding": [
        {
          "system": "http://snomed.info/sct",
          "code": "170430000",
          "display": "Seasonal influenza prevention"
        }
      ],
      "text": "Annual flu prevention - high risk cardiac patient"
    }
  ]
}'

create_resource "Immunization" "$IMMUNIZATION_DATA" "Influenza Vaccine"
IMMUNIZATION_ID=$LAST_CREATED_ID

#===============================================================================
# SUMMARY
#===============================================================================

echo ""
echo -e "${GREEN}"
echo "================================================================================"
echo "                    PATIENT JOURNEY CREATION COMPLETE!"
echo "================================================================================"
echo -e "${NC}"
echo ""
echo -e "${CYAN}Created Resources Summary:${NC}"
echo "─────────────────────────────────────────────────────────────────"
echo ""
printf "  %-25s %s\n" "Resource Type" "ID / URL"
echo "  ─────────────────────────────────────────────────────────────"
printf "  %-25s %s\n" "Organization" "$FHIR_SERVER/Organization/$ORG_ID"
printf "  %-25s %s\n" "Practitioner" "$FHIR_SERVER/Practitioner/$PRACTITIONER_ID"
printf "  %-25s %s\n" "Patient" "$FHIR_SERVER/Patient/$PATIENT_ID"
printf "  %-25s %s\n" "Encounter" "$FHIR_SERVER/Encounter/$ENCOUNTER_ID"
printf "  %-25s %s\n" "AllergyIntolerance" "$FHIR_SERVER/AllergyIntolerance/$ALLERGY1_ID"
printf "  %-25s %s\n" "Condition (HTN)" "$FHIR_SERVER/Condition/$HYPERTENSION_ID"
printf "  %-25s %s\n" "Condition (Angina)" "$FHIR_SERVER/Condition/$ANGINA_ID"
printf "  %-25s %s\n" "DiagnosticReport" "$FHIR_SERVER/DiagnosticReport/$DIAGNOSTIC_REPORT_ID"
printf "  %-25s %s\n" "Procedure" "$FHIR_SERVER/Procedure/$PROCEDURE_ID"
printf "  %-25s %s\n" "CarePlan" "$FHIR_SERVER/CarePlan/$CAREPLAN_ID"
printf "  %-25s %s\n" "Immunization" "$FHIR_SERVER/Immunization/$IMMUNIZATION_ID"
echo ""
echo -e "${YELLOW}Observations created: Blood Pressure, Heart Rate, SpO2, Temperature, Troponin, Cholesterol, BNP${NC}"
echo -e "${YELLOW}Medications created: Lisinopril, Aspirin, Atorvastatin${NC}"
echo ""
echo "─────────────────────────────────────────────────────────────────"
echo ""
echo -e "${CYAN}Quick Access URLs:${NC}"
echo ""
echo "  Patient:     $FHIR_SERVER/Patient/$PATIENT_ID"
echo "  Everything:  $FHIR_SERVER/Patient/$PATIENT_ID/\$everything"
echo ""
echo -e "${GREEN}================================================================================${NC}"
echo ""
